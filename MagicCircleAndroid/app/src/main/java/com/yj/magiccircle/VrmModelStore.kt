package com.yj.magiccircle

import android.content.Context
import android.graphics.BitmapFactory
import android.system.Os
import android.util.JsonReader
import android.util.JsonToken
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.io.RandomAccessFile
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

enum class VrmFormat { V0, V1 }

/** Private, embedded VRM models. Originals are never modified. */
class VrmModelStore(private val root: File) {
    companion object {
        const val MAX_BYTES=64L*1024*1024
        const val MAX_TEXTURE_PIXELS=40L*1024*1024
        @Volatile private var instance: VrmModelStore?=null
        fun get(context: Context): VrmModelStore = instance ?: synchronized(this) {
            instance ?: VrmModelStore(File(context.applicationContext.noBackupFilesDir,"vrm-preview")).also {instance=it}
        }
    }
    private val model get()=File(root,"model.vrm")
    fun hasModel()=model.isFile
    fun openModel(): InputStream? = try {FileInputStream(model)} catch(_: java.io.FileNotFoundException) {null}

    @Synchronized fun importModel(input: InputStream) {
        if(!root.isDirectory && !root.mkdirs())throw IOException("Cannot create private model directory")
        val temp=File.createTempFile("import-",".tmp",root)
        try {
            FileOutputStream(temp).use {output->
                val bytes=ByteArray(32*1024);var total=0L
                while(true) {
                    checkInterrupted()
                    val count=input.read(bytes,0,minOf(bytes.size.toLong(),MAX_BYTES-total+1).toInt())
                    if(count<0)break
                    if(count==0)throw IOException("File provider stopped returning data")
                    total+=count
                    require(total<=MAX_BYTES) {"Model exceeds 64 MiB"}
                    output.write(bytes,0,count)
                }
                output.fd.sync()
            }
            validate(temp)
            checkInterrupted()
            // POSIX rename replaces atomically on the same private filesystem, or throws.
            Os.rename(temp.path,model.path)
        } catch(e: Exception) {throw IOException("VRM import failed; previous model preserved",e)}
        finally {temp.delete()}
    }

    private fun checkInterrupted() {if(Thread.currentThread().isInterrupted)throw InterruptedIOException("Import cancelled")}
    private fun number(obj: JSONObject,key: String,default: Long?=null): Long {
        if(!obj.has(key) && default!=null)return default
        val raw=obj.get(key)
        require(raw is Number) {"Expected integer $key"}
        val n=raw.toDouble()
        require(n.isFinite() && n>=0 && n<=Int.MAX_VALUE && n==raw.toLong().toDouble()) {"Invalid integer $key"}
        return raw.toLong()
    }
    private fun array(obj: JSONObject,key: String,max: Int): JSONArray {
        val result=if(obj.has(key))obj.getJSONArray(key)else JSONArray()
        require(result.length()<=max) {"Too many $key"}
        return result
    }
    private fun validate(file: File): VrmFormat = RandomAccessFile(file,"r").use {data->
        fun u32()=Integer.reverseBytes(data.readInt()).toLong() and 0xffffffffL
        require(file.length() in 28..MAX_BYTES && file.length()%4L==0L) {"Invalid GLB size"}
        require(u32()==0x46546c67L && u32()==2L && u32()==file.length()) {"Invalid GLB header"}
        val jsonSize=u32()
        require(jsonSize in 4..4L*1024*1024 && jsonSize%4L==0L && jsonSize<=file.length()-20 && u32()==0x4e4f534aL) {"Invalid JSON chunk"}
        val bytes=ByteArray(jsonSize.toInt());data.readFully(bytes)
        val text=Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
        // JsonReader still accepts some JavaScript-incompatible strings/literals in strict mode.
        val jsonNumber=Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")
        var pos=0
        while(pos<text.length) {
            val c=text[pos++]
            if(c in "{}[],: \t\r\n")continue
            if(c=='"') {
                var terminated=false
                while(pos<text.length) {
                    val ch=text[pos++]
                    require(ch>=' ') {"Unescaped JSON control character"}
                    if(ch=='"') {terminated=true;break}
                    if(ch=='\\') {
                        require(pos<text.length)
                        val escape=text[pos++]
                        if(escape=='u') {
                            require(pos+4<=text.length && (pos until pos+4).all {text[it] in "0123456789abcdefABCDEF"}) {"Invalid Unicode escape"}
                            pos+=4
                        } else require(escape in "\"\\/bfnrt") {"Invalid JSON escape"}
                    }
                }
                require(terminated) {"Unterminated JSON string"}
            } else {
                val start=pos-1
                while(pos<text.length && text[pos] !in "{}[],: \t\r\n\"")pos++
                val token=text.substring(start,pos)
                require(token=="true" || token=="false" || token=="null" || jsonNumber.matches(token)) {"Invalid JSON literal"}
            }
        }
        // Enforce JSON syntax and nesting before the permissive recursive org.json parser.
        JsonReader(StringReader(text)).use {reader->
            reader.isLenient=false
            var depth=0
            while(reader.peek()!=JsonToken.END_DOCUMENT) {
                when(reader.peek()) {
                    JsonToken.BEGIN_ARRAY->{require(++depth<=64);reader.beginArray()}
                    JsonToken.BEGIN_OBJECT->{require(++depth<=64);reader.beginObject()}
                    JsonToken.END_ARRAY->{depth--;reader.endArray()}
                    JsonToken.END_OBJECT->{depth--;reader.endObject()}
                    JsonToken.NAME->reader.nextName()
                    JsonToken.STRING,JsonToken.NUMBER->reader.nextString()
                    JsonToken.BOOLEAN->reader.nextBoolean()
                    JsonToken.NULL->reader.nextNull()
                    else->error("Invalid JSON")
                }
            }
            require(depth==0)
        }
        val tokener=JSONTokener(text)
        val json=tokener.nextValue() as? JSONObject ?: throw IOException("Missing glTF document")
        require(tokener.nextClean()=='\u0000') {"Trailing JSON data"}
        require(data.filePointer+8<=file.length()) {"Missing embedded BIN"}
        val binSize=u32()
        require(u32()==0x004e4942L && binSize%4L==0L && data.filePointer+binSize==file.length()) {"Invalid BIN chunk"}
        val binStart=data.filePointer
        require(json.getJSONObject("asset").getString("version")=="2.0")
        val extensions=json.getJSONObject("extensions")
        require(extensions.has("VRM") xor extensions.has("VRMC_vrm")) {"Expected exactly one VRM extension"}
        val format=if(extensions.has("VRM"))VrmFormat.V0 else VrmFormat.V1
        val key=if(format==VrmFormat.V0)"VRM" else "VRMC_vrm"
        val vrm=extensions.getJSONObject(key)
        require(vrm.getString("specVersion")==if(format==VrmFormat.V0)"0.0" else "1.0") {"Unsupported VRM version"}
        require((0 until json.getJSONArray("extensionsUsed").length()).any {json.getJSONArray("extensionsUsed").getString(it)==key})
        val meta=vrm.getJSONObject("meta")
        if(format==VrmFormat.V1) {
            require(meta.getString("name").isNotBlank() && meta.getString("licenseUrl").isNotBlank())
            val authors=meta.getJSONArray("authors");require(authors.length()>0)
            for(i in 0 until authors.length())require(authors.getString(i).isNotBlank())
        } else require(listOf("title","author","licenseName").all {meta.getString(it).isNotBlank()})
        val nodes=array(json,"nodes",4096);require(nodes.length()>0)
        val bones=mutableMapOf<String,Long>()
        val human=vrm.getJSONObject("humanoid")
        if(format==VrmFormat.V1) {
            val entries=human.getJSONObject("humanBones")
            entries.keys().forEach {bone->bones[bone]=number(entries.getJSONObject(bone),"node")}
        } else {
            val entries=human.getJSONArray("humanBones");require(entries.length()<=256)
            for(i in 0 until entries.length()) {
                val entry=entries.getJSONObject(i);val bone=entry.getString("bone")
                require(bone.isNotBlank() && !bones.containsKey(bone)) {"Duplicate humanoid bone"}
                bones[bone]=number(entry,"node")
            }
        }
        require(bones.values.all {it<nodes.length()} && bones.values.toSet().size==bones.size) {"Invalid or duplicate humanoid node"}
        for(bone in listOf("hips","spine","head","leftUpperLeg","leftLowerLeg","leftFoot","rightUpperLeg","rightLowerLeg","rightFoot","leftUpperArm","leftLowerArm","leftHand","rightUpperArm","rightLowerArm","rightHand")) {
            require(bones.containsKey(bone)) {"Missing humanoid bone"}
        }
        if(format==VrmFormat.V0)require(bones.containsKey("chest") && bones.containsKey("neck")) {"Missing legacy humanoid bone"}
        val buffers=json.getJSONArray("buffers");require(buffers.length()==1)
        val buffer=buffers.getJSONObject(0);require(!buffer.has("uri")) {"External buffers are not supported"}
        val length=number(buffer,"byteLength")
        require(length>0 && length<=binSize && binSize-length<=3) {"Invalid embedded buffer length"}
        val views=array(json,"bufferViews",8192)
        for(i in 0 until views.length()) {
            val view=views.getJSONObject(i)
            require(number(view,"buffer")==0L)
            require(number(view,"byteLength")>0 && number(view,"byteOffset",0)+number(view,"byteLength")<=length) {"Buffer view out of bounds"}
            if(view.has("byteStride"))require(number(view,"byteStride") in 4..252 && number(view,"byteStride")%4L==0L)
        }
        fun viewAt(index: Long): JSONObject {require(index<views.length());return views.getJSONObject(index.toInt())}
        fun span(view: JSONObject,offset: Long,count: Long,size: Long,stride: Long=size) {
            require(stride>=size && offset+(count-1)*stride+size<=number(view,"byteLength")) {"Accessor out of bounds"}
        }
        val accessors=array(json,"accessors",8192);var decodedBytes=0L
        for(i in 0 until accessors.length()) {
            val accessor=accessors.getJSONObject(i)
            val component=when(number(accessor,"componentType")) {5120L,5121L->1L;5122L,5123L->2L;5125L,5126L->4L;else->error("Invalid component")}
            val shape=when(accessor.getString("type")) {"SCALAR"->1 to 1;"VEC2"->2 to 1;"VEC3"->3 to 1;"VEC4"->4 to 1;"MAT2"->2 to 2;"MAT3"->3 to 3;"MAT4"->4 to 4;else->error("Invalid accessor type")}
            val column=shape.first*component
            val size=(if(shape.second>1)(column+3)/4*4 else column)*shape.second
            val count=number(accessor,"count");require(count>0)
            decodedBytes+=count*shape.first*shape.second*4
            require(decodedBytes<=128L*1024*1024) {"Model geometry too large"}
            val offset=number(accessor,"byteOffset",0)
            require(offset%component==0L)
            if(accessor.has("bufferView")) {
                val view=viewAt(number(accessor,"bufferView"))
                require((number(view,"byteOffset",0)+offset)%component==0L)
                val stride=number(view,"byteStride",size);require(stride%component==0L)
                span(view,offset,count,size,stride)
            } else require(offset==0L)
            if(accessor.has("sparse")) {
                val sparse=accessor.getJSONObject("sparse");val sparseCount=number(sparse,"count")
                require(sparseCount in 1..count)
                val indices=sparse.getJSONObject("indices")
                val indexSize=when(number(indices,"componentType")) {5121L->1L;5123L->2L;5125L->4L;else->error("Invalid sparse index")}
                val indexView=viewAt(number(indices,"bufferView"));val indexOffset=number(indices,"byteOffset",0)
                require(!indexView.has("byteStride") && indexOffset%indexSize==0L)
                span(indexView,indexOffset,sparseCount,indexSize)
                data.seek(binStart+number(indexView,"byteOffset",0)+indexOffset)
                var previous=-1L
                repeat(sparseCount.toInt()) {
                    val index=when(indexSize) {1L->data.readUnsignedByte().toLong();2L->java.lang.Short.reverseBytes(data.readShort()).toLong() and 65535L;else->u32()}
                    require(index>previous && index<count);previous=index
                }
                val values=sparse.getJSONObject("values");val valueView=viewAt(number(values,"bufferView"));val valueOffset=number(values,"byteOffset",0)
                require(!valueView.has("byteStride") && valueOffset%component==0L)
                span(valueView,valueOffset,sparseCount,size)
            }
        }
        val images=array(json,"images",64);var pixels=0L
        for(i in 0 until images.length()) {
            checkInterrupted()
            val image=images.getJSONObject(i);require(!image.has("uri")) {"External images are not supported"}
            require(image.getString("mimeType") in listOf("image/png","image/jpeg"))
            val view=viewAt(number(image,"bufferView"));val imageSize=number(view,"byteLength")
            require(imageSize<=16L*1024*1024) {"Embedded image too large"}
            data.seek(binStart+number(view,"byteOffset",0))
            val encoded=ByteArray(imageSize.toInt());data.readFully(encoded)
            val options=BitmapFactory.Options().apply {inJustDecodeBounds=true}
            BitmapFactory.decodeByteArray(encoded,0,encoded.size,options)
            require(options.outMimeType==image.getString("mimeType")) {"Image MIME mismatch"}
            require(options.outWidth in 1..4096 && options.outHeight in 1..4096) {"Texture exceeds 4096 pixels"}
            pixels+=options.outWidth.toLong()*options.outHeight
            require(pixels<=MAX_TEXTURE_PIXELS) {"Total texture size too large"}
        }
        array(json,"meshes",256);array(json,"materials",256)
        format
    }
}
