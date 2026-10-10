package com.yj.magiccircle

import java.io.File
import java.io.Closeable
import java.io.RandomAccessFile
import java.io.InterruptedIOException
import java.security.MessageDigest
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.CodingErrorAction
import org.json.JSONObject
import org.json.JSONArray
import org.json.JSONTokener

data class VrmHairPart(val id: String,val styleId: String,val baseModelId: String,val sourceModelId: String,val directory: File)
data class VrmHairAssemblyResult(val file: File,val modelId: String,val partId: String,val protectedDigest: String,val hairDigest: String)

object VrmHairPartCodec {
    fun read(directory: File): VrmHairPart = load(directory,128L*1024*1024).use {it.part}
    internal fun load(directory: File,budgetBytes: Long): HairPartData {
        val jsonFile=File(directory,"part.json");val bin=File(directory,"part.bin")
        require(jsonFile.isFile&&jsonFile.length() in 1..4L*1024*1024&&bin.isFile&&bin.length()>0&&jsonFile.length()+bin.length()<=VrmModelStore.MAX_BYTES)
        require(jsonFile.length()*16+16L*1024*1024<=budgetBytes){"Insufficient part memory"}
        val bytes=jsonFile.readBytes();val d=hairParse(bytes)
        require(d["schemaVersion"].hi()==1&&d["kind"]=="hair"&&d["styleId"] in listOf("e-original","e-hair02"))
        for(key in listOf("baseModelId","sourceModelId","protectedDigest","hairDigest","binSha256"))require(d[key].hs().matches(Regex("[a-f0-9]{64}")))
        require(d["binLength"].hi().toLong()==bin.length()&&d["binSha256"]==hairHash(bin)){"Damaged part BIN"}
        val local=d.objects("nodes");val anchors=d["anchors"].list().map {it.hs()};require(local.size<=4096&&anchors.size<=4096&&anchors.toSet().size==anchors.size)
        require(anchors.all {it.isNotEmpty()&&it.length<=32768})
        val nodes=local.map {n->hairCopy(n["node"]).obj().also {require(listOf("mesh","skin","children").none(it::containsKey))}}.toMutableList()
        val anchorIndices=anchors.mapIndexed {i,s->s to (nodes.size+i)}.toMap()
        nodes.addAll(anchors.indices.map {ho("name" to "anchor-$it")})
        fun ref(v: Any?): Int {val r=v.obj();require(r.size==1);return if(r.containsKey("local"))r["local"].hi().also {require(it<local.size)}else checkNotNull(anchorIndices[r["anchor"].hs()])}
        for((i,n) in local.withIndex())if(n["parent"]!=null){val parent=nodes[ref(n["parent"])];parent["children"]=(parent.optional("children")+i).toMutableList()}
        val geometry=d.objects("geometry");require(geometry.size in 1..4096)
        val meshes=mutableListOf<HairObject>();val skins=mutableListOf<HairObject>()
        for(g in geometry) {
            val node=nodes[ref(g["target"])];require(!node.containsKey("mesh"));node["mesh"]=meshes.size;node["skin"]=skins.size
            meshes.add(hairCopy(g["mesh"]).obj());val s=hairCopy(g["skin"]).obj();s["joints"]=s["joints"].list().map(::ref);if(s["skeleton"]!=null)s["skeleton"]=ref(s["skeleton"]);skins.add(s)
        }
        for(s in d.objects("springs")){s.objects("joints").forEach {ref(it["node"])};if(s["center"]!=null)ref(s["center"])}
        d.objects("annotations").forEach {ref(it["node"])}
        require(d.objects("materials").all {it["name"] in hairNames}){"Protected part material"}
        val projection=ho("asset" to ho("version" to "2.0"),"nodes" to nodes,"meshes" to meshes,"skins" to skins,"scenes" to listOf(ho("nodes" to emptyList<Any>())),"scene" to 0,"buffers" to listOf(ho("byteLength" to bin.length())),
            "extensions" to ho("VRMC_vrm" to ho("specVersion" to "1.0","humanoid" to ho("humanBones" to ho("head" to ho("node" to 0),"hips" to ho("node" to 0))))))
        hairTables.forEach {projection[it]=hairCopy(d[it])}
        val document=HairDocument(projection,bin,0,bin.length())
        try {
            document.validateGeometry()
            val md=MessageDigest.getInstance("SHA-256");md.update(bytes);document.copySpan(0,bin.length()){b,n->md.update(b,0,n)}
            return HairPartData(VrmHairPart(hairHex(md.digest()),d["styleId"].hs(),d["baseModelId"].hs(),d["sourceModelId"].hs(),directory),d,document)
        }catch(e: Exception){document.close();throw e}
    }
}
internal data class HairPartData(val part: VrmHairPart,val json: HairObject,val source: HairDocument): Closeable {override fun close()=source.close()}

internal typealias HairObject = MutableMap<String,Any?>
internal fun ho(vararg pairs: Pair<String,Any?>): HairObject = linkedMapOf(*pairs)
@Suppress("UNCHECKED_CAST") internal fun Any?.obj(): HairObject = this as? HairObject ?: error("Expected object")
@Suppress("UNCHECKED_CAST") internal fun Any?.list(): List<Any?> = this as? List<Any?> ?: error("Expected array")
internal fun HairObject.objects(key: String)=get(key).list().map {it.obj()}
internal fun HairObject.optional(key: String)=get(key)?.list() ?: emptyList()
internal fun Any?.hi(): Int {val n=this as? Number ?: error("Expected integer");val d=n.toDouble();require(d.isFinite()&&d>=0&&d<=Int.MAX_VALUE&&d==n.toInt().toDouble());return n.toInt()}
internal fun Any?.hd(): Double=(this as? Number)?.toDouble()?.also {require(it.isFinite())} ?: error("Expected number")
internal fun Any?.hs(): String=this as? String ?: error("Expected string")
internal fun hairCheckInterrupt() {if(Thread.currentThread().isInterrupted)throw InterruptedIOException("Hair operation cancelled")}
internal fun hairHex(bytes: ByteArray)=bytes.joinToString(""){"%02x".format(it)}
internal fun hairHash(file: File): String {
    val md=MessageDigest.getInstance("SHA-256");file.inputStream().use {input->val b=ByteArray(32768);while(true){hairCheckInterrupt();val n=input.read(b);if(n<0)break;require(n>0);md.update(b,0,n)}}
    return hairHex(md.digest())
}
internal fun hairHash(bytes: ByteArray)=hairHex(MessageDigest.getInstance("SHA-256").digest(bytes))
internal fun hairJson(value: Any?,sort: Boolean=true,fingerprint: Boolean=false): String=when(value) {
    null,JSONObject.NULL->"null"
    is String->JSONObject.quote(value).replace("\\/","/")
    is Boolean->value.toString()
    is Number->{val n=value.toDouble();require(n.isFinite());if(fingerprint)"#"+java.lang.Long.toHexString(java.lang.Double.doubleToLongBits(if(n==0.0)0.0 else n)).padStart(16,'0') else if(n==0.0)"0" else {
        val decimal=java.math.BigDecimal.valueOf(n).stripTrailingZeros()
        if(kotlin.math.abs(n)>=1e-6&&kotlin.math.abs(n)<1e21)decimal.toPlainString()
        else decimal.toString().lowercase(java.util.Locale.ROOT).replace(Regex("e(-?)0+"),"e$1").let {if('e' in it&&"e-" !in it&&"e+" !in it)it.replace("e","e+")else it}
    }}
    is Map<*,*>->{val keys=value.keys.map {it as String}.let {if(sort)it.sorted()else it};keys.joinToString(",","{","}"){hairJson(it)+":"+hairJson(value[it],sort,fingerprint)}}
    is Iterable<*>->value.joinToString(",","[","]"){hairJson(it,sort,fingerprint)}
    is DoubleArray->value.joinToString(",","[","]"){hairJson(it,sort,fingerprint)}
    else->error("Unsupported JSON value")
}
// IEEE-754 tokens avoid Java/JavaScript's different decimal rounding in proof hashes.
internal fun hairDigest(value: Any?)=hairHash(hairJson(value,fingerprint=true).toByteArray(Charsets.UTF_8))
internal fun hairCopy(value: Any?): Any?=when(value) {
    is Map<*,*>->value.entries.associateTo(linkedMapOf<String,Any?>()){it.key as String to hairCopy(it.value)}
    is List<*>->value.map(::hairCopy).toMutableList()
    else->value
}
internal fun hairParse(bytes: ByteArray): HairObject {
    require(bytes.size in 1..4*1024*1024)
    val text=Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
    validateVrmJson(text)
    val tokener=JSONTokener(text);val raw=tokener.nextValue();require(tokener.nextClean()=='\u0000')
    fun convert(v: Any?): Any?=when(v) {
        null,JSONObject.NULL->null
        is JSONObject->v.keys().asSequence().associateTo(linkedMapOf()){it to convert(v.get(it))}
        is JSONArray->(0 until v.length()).map {convert(v.get(it))}.toMutableList()
        is String,is Boolean->v
        is Number->v.also {require(it.toDouble().isFinite())}
        else->error("Invalid JSON value")
    }
    val result=convert(raw).obj()
    return result
}

internal val hairNames=setOf("N00_000_00_HairBack_00_HAIR (Instance)","N00_000_Hair_00_HAIR (Instance)")+listOf("01","02","03").map {"N00_000_Hair_00_HAIR_$it (Instance)"}
internal val hairWidths=mapOf("SCALAR" to 1,"VEC2" to 2,"VEC3" to 3,"VEC4" to 4,"MAT4" to 16)
internal val hairTables=listOf("accessors","bufferViews","materials","textures","images","samplers")

/** Encoded images are streamed; only a bounded geometry working set is retained. */
internal class HairDocument(val json: HairObject,val file: File,val binStart: Long,val binLength: Long): Closeable {
    private val handle=lazy {RandomAccessFile(file,"r")}
    private val data by handle
    private val cache=linkedMapOf<Int,DoubleArray>();private var cachedBytes=0L
    val nodes get()=json.objects("nodes")
    val meshes get()=json.objects("meshes")
    val skins get()=json.objects("skins")
    val materials get()=json.objects("materials")
    val parents: IntArray
    val paths: List<String>
    val hairRefs: Set<Pair<Int,Int>>
    init {
        val counts=mapOf("nodes" to 4096,"meshes" to 4096,"skins" to 256,"accessors" to 8192,"bufferViews" to 8192,"materials" to 256,"images" to 64,"textures" to 256,"samplers" to 256,"scenes" to 32)
        for((k,max) in counts){if(json[k]==null)json[k]=mutableListOf<Any?>();require(json[k].list().size<=max)}
        require(binLength in 1..VrmModelStore.MAX_BYTES)
        fun noExternal(v: Any?) {when(v){is Map<*,*>->{require(!v.containsKey("uri"));v.values.forEach(::noExternal)};is List<*>->v.forEach(::noExternal)}}
        noExternal(json)
        for(v in json.objects("bufferViews"))require(v["buffer"].hi()==0&&v["byteStride"]==null&&(v["byteOffset"]?:0).hi().toLong()+v["byteLength"].hi()<=binLength&&v["byteLength"].hi()>0)
        var decoded=0L
        for(a in json.objects("accessors")) {
            val width=hairWidths[a["type"].hs()] ?: error("Accessor type");val component=a["componentType"].hi();require(component in setOf(5123,5125,5126)&&a["sparse"]==null&&a["normalized"]!=true)
            val size=if(component==5123)2 else 4;val count=a["count"].hi();require(count>0)
            val v=json.objects("bufferViews")[a["bufferView"].hi()];val offset=(a["byteOffset"]?:0).hi()
            require(offset%size==0&&((v["byteOffset"]?:0).hi()+offset)%size==0&&offset.toLong()+count.toLong()*width*size<=v["byteLength"].hi())
            decoded+=count.toLong()*width*8;require(decoded<=128L*1024*1024)
        }
        parents=IntArray(nodes.size){-1}
        for((i,n) in nodes.withIndex())for(child in n.optional("children")){val c=child.hi();require(c in nodes.indices&&parents[c]==-1);parents[c]=i}
        val names=arrayOfNulls<String>(nodes.size);val visiting=mutableSetOf<Int>()
        fun path(i: Int,depth: Int=0): String {
            names[i]?.let {return it};require(depth<=128&&visiting.add(i));val name=nodes[i]["name"].hs();require(name.isNotBlank()&&!name.contains('/'))
            val value=if(parents[i]<0)name else path(parents[i],depth+1)+"/"+name;visiting.remove(i);names[i]=value;return value
        }
        paths=nodes.indices.map {path(it)};require(paths.toSet().size==paths.size)
        hairRefs=meshes.flatMapIndexed {mi,m->m.objects("primitives").mapIndexedNotNull {pi,p->if(materials[p["material"].hi()]["name"] in hairNames)mi to pi else null}}.toSet()
        require(hairRefs.isNotEmpty())
    }
    fun accessor(index: Int): DoubleArray {
        cache[index]?.let {return it};hairCheckInterrupt()
        val a=json.objects("accessors")[index];val width=hairWidths.getValue(a["type"].hs());val component=a["componentType"].hi();val size=if(component==5123)2 else 4
        val v=json.objects("bufferViews")[a["bufferView"].hi()];val n=a["count"].hi()*width;val out=DoubleArray(n)
        data.seek(binStart+(v["byteOffset"]?:0).hi()+(a["byteOffset"]?:0).hi());val b=ByteArray(32768);var done=0
        while(done<n){hairCheckInterrupt();val count=minOf(n-done,b.size/size);data.readFully(b,0,count*size);val bb=ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
            repeat(count){val value=when(component){5123->(bb.short.toInt() and 65535).toDouble();5125->(bb.int.toLong() and 0xffffffffL).toDouble();else->bb.float.toDouble()};require(value.isFinite());out[done++]=value}}
        if(out.size*8L<=8L*1024*1024){while(cachedBytes+out.size*8L>8L*1024*1024&&cache.isNotEmpty()){val key=cache.keys.first();cachedBytes-=cache.remove(key)!!.size*8L};cache[index]=out;cachedBytes+=out.size*8L}
        return out
    }
    fun imageHash(index: Int): String {val image=json.objects("images")[index];val v=json.objects("bufferViews")[image["bufferView"].hi()];return spanHash((v["byteOffset"]?:0).hi().toLong(),v["byteLength"].hi().toLong())}
    fun spanHash(offset: Long,length: Long): String {val md=MessageDigest.getInstance("SHA-256");copySpan(offset,length){bytes,n->md.update(bytes,0,n)};return hairHex(md.digest())}
    fun copySpan(offset: Long,length: Long,write: (ByteArray,Int)->Unit) {
        require(offset>=0&&length>0&&offset+length<=binLength);data.seek(binStart+offset);val b=ByteArray(32768);var left=length
        while(left>0){hairCheckInterrupt();val n=minOf(left,b.size.toLong()).toInt();data.readFully(b,0,n);write(b,n);left-=n}
    }
    override fun close(){cache.clear();cachedBytes=0;if(handle.isInitialized())data.close()}
    companion object {
        fun open(file: File,budgetBytes: Long): HairDocument {
            require(budgetBytes>=8L*1024*1024&&file.length() in 28..VrmModelStore.MAX_BYTES&&file.length()%4==0L)
            RandomAccessFile(file,"r").use {r->
                fun u32()=Integer.reverseBytes(r.readInt()).toLong() and 0xffffffffL
                require(u32()==0x46546c67L&&u32()==2L&&u32()==file.length());val size=u32()
                require(size in 4..4L*1024*1024&&size%4==0L&&size+28<=file.length()&&u32()==0x4e4f534aL)
                require(size*16+8L*1024*1024<=budgetBytes){"Insufficient assembly memory"}
                val b=ByteArray(size.toInt());r.readFully(b);val json=hairParse(b);val length=u32();require(u32()==0x004e4942L&&length+r.filePointer==file.length())
                require(json["asset"].obj()["version"]=="2.0"&&json["extensions"].obj()["VRMC_vrm"].obj()["specVersion"]=="1.0")
                val peak=json.objects("accessors").maxOf {it["count"].hi().toLong()*(hairWidths[it["type"]]?:16)*16}
                require(size*16+peak*4+8L*1024*1024<=budgetBytes){"Insufficient geometry memory"}
                return HairDocument(json,file,r.filePointer,length)
            }
        }
    }
}

internal data class HairOwnership(val nodes: Set<Int>,val meshNodes: Set<Int>,val springs: Set<Int>)
internal fun HairDocument.used(primitives: List<HairObject>): List<Int> {
    val result=mutableSetOf<Int>()
    for(p in primitives) {
        val attrs=p["attributes"].obj();val joints=accessor(attrs["JOINTS_0"].hi());val weights=accessor(attrs["WEIGHTS_0"].hi())
        require(joints.size==weights.size)
        for(vertex in accessor(p["indices"].hi()).toSet()) {val v=vertex.hi();require(v*4+3<joints.size)
            for(k in 0..3){val w=weights[v*4+k];require(w in 0.0..1.0);if(w>0)result.add(joints[v*4+k].hi())}}
    }
    require(result.isNotEmpty());return result.sorted()
}

internal fun HairDocument.validateGeometry() {
    val accessors=json.objects("accessors");val views=json.objects("bufferViews");val images=json.objects("images");val textures=json.objects("textures");val samplers=json.objects("samplers")
    require(materials.map {it["name"]}.toSet().size==materials.size)
    val supported=setOf("VRMC_vrm","VRMC_springBone","VRMC_materials_mtoon","KHR_texture_transform","KHR_materials_unlit")
    fun walk(v: Any?,depth: Int=0) {
        require(depth<=64)
        when(v){is Map<*,*>->{require(!v.containsKey("uri"));v["extensions"]?.obj()?.keys?.forEach {require(it in supported)}
            for((k,value) in v){if((k as String).endsWith("Texture")&&value is Map<*,*>&&value.containsKey("index"))require(value["index"].hi()<textures.size);walk(value,depth+1)}}
            is List<*>->v.forEach {walk(it,depth+1)}}
    };walk(json);require(json.optional("animations").isEmpty())
    for(e in json.optional("extensionsUsed")+json.optional("extensionsRequired"))require(e in supported)
    var pixels=0L
    for(image in images) {
        require(image["mimeType"]=="image/png"){"Unreviewed image encoding"};val v=views[image["bufferView"].hi()];require(v["byteLength"].hi()>=24)
        val header=ByteArray(24);var filled=0
        copySpan((v["byteOffset"]?:0).hi().toLong(),24){b,n->b.copyInto(header,filled,0,n);filled+=n}
        require(header.copyOfRange(0,8).contentEquals(byteArrayOf(-119,80,78,71,13,10,26,10))&&String(header,12,4,Charsets.US_ASCII)=="IHDR")
        val b=ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN);val w=b.getInt(16);val h=b.getInt(20);require(w in 1..4096&&h in 1..4096);pixels+=w.toLong()*h;require(pixels<=VrmModelStore.MAX_TEXTURE_PIXELS)
    }
    for(t in textures){require(t["source"].hi()<images.size);if(t["sampler"]!=null)require(t["sampler"].hi()<samplers.size)}
    for(s in skins) {val joints=s["joints"].list().map {it.hi()};require(joints.isNotEmpty()&&joints.toSet().size==joints.size&&joints.all {it in nodes.indices});if(s["skeleton"]!=null)require(s["skeleton"].hi() in nodes.indices)
        val a=accessors[s["inverseBindMatrices"].hi()];require(a["type"]=="MAT4"&&a["componentType"].hi()==5126&&a["count"].hi()==joints.size)}
    for(a in accessors.indices)accessor(a)
    val types=mapOf("POSITION" to "VEC3","NORMAL" to "VEC3","TEXCOORD_0" to "VEC2","JOINTS_0" to "VEC4","WEIGHTS_0" to "VEC4")
    for(node in nodes)if(node.containsKey("mesh")) {
        val mesh=meshes[node["mesh"].hi()];val skin=skins[node["skin"].hi()];val primitives=mesh.objects("primitives");require(primitives.size in 1..256)
        for(p in primitives) {
            require((p["mode"]?:4).hi()==4&&p["material"].hi()<materials.size);val attrs=p["attributes"].obj();val count=accessors[attrs["POSITION"].hi()]["count"].hi()
            require(attrs.keys.containsAll(listOf("POSITION","JOINTS_0","WEIGHTS_0")))
            for((key,index) in attrs){val a=accessors[index.hi()];require(a["count"].hi()==count&&a["type"]==types[key]&&a["componentType"].hi()==if(key=="JOINTS_0")5123 else 5126)}
            for(t in p.optional("targets"))for((key,index) in t.obj()){val a=accessors[index.hi()];require(key in listOf("POSITION","NORMAL")&&a["type"]=="VEC3"&&a["count"].hi()==count&&a["componentType"].hi()==5126)}
            val a=accessors[p["indices"].hi()];require(a["type"]=="SCALAR"&&a["componentType"].hi() in listOf(5123,5125)&&a["count"].hi()%3==0)
            for(v in accessor(p["indices"].hi()))require(v.hi()<count)
            val joints=accessor(attrs["JOINTS_0"].hi());val weights=accessor(attrs["WEIGHTS_0"].hi());require(joints.size==weights.size)
            for(i in joints.indices)require(joints[i].hi()<skin["joints"].list().size&&weights[i] in 0.0..1.0)
        }
    }
}
internal fun HairDocument.ownership(): HairOwnership {
    val protected=mutableSetOf<Int>();val hair=mutableSetOf<Int>();val meshNodes=mutableSetOf<Int>()
    for((ni,node) in nodes.withIndex())if(node.containsKey("mesh")) {
        val mi=node["mesh"].hi();val primitives=meshes[mi].objects("primitives");val skin=skins[node["skin"].hi()]
        if(primitives.indices.all {mi to it in hairRefs}){meshNodes.add(ni);require(node.optional("children").isEmpty())}
        for((pi,p) in primitives.withIndex())for(i in used(listOf(p))) {
            val target=if(mi to pi in hairRefs)hair else protected;target.add(skin["joints"].list()[i].hi())
        }
    }
    json["extensions"].obj()["VRMC_vrm"].obj()["humanoid"].obj()["humanBones"].obj().values.forEach {protected.add(it.obj()["node"].hi())}
    for(n in protected.toList()){var p=parents[n];while(p>=0){protected.add(p);p=parents[p]}}
    val owned=mutableSetOf<Int>()
    fun include(n: Int){if(n in owned)return;require(n !in protected&&!nodes[n].containsKey("mesh")){"Mixed hair/protected branch"};owned.add(n);nodes[n].optional("children").forEach {include(it.hi())}}
    for(n in hair)if(n !in protected){var root=n;while(parents[root]>=0&&parents[root] !in protected)root=parents[root];include(root)}
    val springs=mutableSetOf<Int>();val spring=json["extensions"].obj()["VRMC_springBone"]?.obj()
    for((i,s) in (spring?.optional("springs")?:emptyList()).withIndex()) {
        val chain=s.obj();val flags=chain.objects("joints").map {it["node"].hi() in owned}
        if(flags.any {it}){require(flags.all {it}){"Mixed hair and protected spring"};springs.add(i)}
        if(chain["center"]!=null&&chain["center"].hi() in owned)require(flags.all {it})
    }
    for(c in spring?.optional("colliders")?:emptyList())require(c.obj()["node"].hi() !in owned){"Hair collider requires review"}
    val expr=json["extensions"].obj()["VRMC_vrm"].obj()["expressions"]?.obj()?:ho()
    for(e in (expr["preset"]?.obj()?.values?:emptyList())+(expr["custom"]?.obj()?.values?:emptyList())) {
        val expression=e.obj()
        for(b in expression.optional("morphTargetBinds"))require(b.obj()["node"].hi() !in owned&&b.obj()["node"].hi() !in meshNodes)
        for(b in expression.optional("materialColorBinds")+expression.optional("textureTransformBinds"))require(materials[b.obj()["material"].hi()]["name"] !in hairNames)
    }
    return HairOwnership(owned,meshNodes,springs)
}

internal data class HairFingerprints(val protectedDigest: String,val hairDigest: String,val world: String,val shapes: Map<String,String>)
internal fun HairDocument.fingerprints(): HairFingerprints {
    val ownership=ownership();val removed=ownership.nodes+ownership.meshNodes;val rig=ho();val hairRig=ho();val shapes=mutableMapOf<String,String>();val hairs=mutableSetOf<String>()
    var work=0L
    fun refs(v: Any?): Any?=when(v) {
        is Map<*,*>->v.entries.associateTo(linkedMapOf<String,Any?>()){val k=it.key as String;k to when {k in setOf("node","center")&&it.value is Number->paths[it.value.hi()];k=="material"&&it.value is Number->materials[it.value.hi()]["name"];else->refs(it.value)}}
        is List<*>->v.map(::refs);else->v
    }
    fun texture(index: Int): HairObject {
        val t=hairCopy(json.objects("textures")[index]).obj();val source=t.remove("source").hi();t.remove("name");val sampler=t.remove("sampler")
        t["image"]=ho("mimeType" to json.objects("images")[source]["mimeType"],"sha256" to imageHash(source));t["sampler"]=sampler?.let {json.objects("samplers")[it.hi()]};return t
    }
    fun material(v: Any?): Any?=when(v) {
        is Map<*,*>->v.entries.associateTo(linkedMapOf<String,Any?>()){val k=it.key as String;val value=it.value;k to if(k.endsWith("Texture")&&value is Map<*,*>&&value.containsKey("index")){val t=hairCopy(value).obj();t["texture"]=texture(t.remove("index").hi());t}else material(value)}
        is List<*>->v.map(::material);else->v
    }
    fun bindings(skin: HairObject,primitives: List<HairObject>): List<Any?> {
        if(primitives.isEmpty())return emptyList()
        val matrix=accessor(skin["inverseBindMatrices"].hi());val joints=skin["joints"].list()
        return used(primitives).map {i->require(i<joints.size&&i*16+16<=matrix.size);listOf(paths[joints[i].hi()],matrix.copyOfRange(i*16,i*16+16).toList())}.sortedBy {it[0] as String}
    }
    fun values(index: Int,vertices: DoubleArray,skin: HairObject,weights: DoubleArray?=null): String {
        val a=json.objects("accessors")[index];val data=accessor(index);val width=hairWidths.getValue(a["type"].hs());val md=MessageDigest.getInstance("SHA-256")
        work+=vertices.size.toLong()*width;require(work<=16L*1024*1024){"Comparison work limit"}
        for(vertex in vertices) {val v=vertex.hi();require(v.toLong()*width+width<=data.size)
            val tuple=(0 until width).map {k->if(weights!=null){if(weights[v*4+k]==0.0)null else paths[skin["joints"].list()[data[v*width+k].hi()].hi()]}else data[v*width+k]}
            md.update(hairJson(tuple,false,true).toByteArray(Charsets.UTF_8))}
        return hairHex(md.digest())
    }
    for((ni,node) in nodes.withIndex()) {
        hairCheckInterrupt();val rest=hairCopy(node).obj();val mi=rest.remove("mesh")?.hi();val si=rest.remove("skin")?.hi();val children=rest.remove("children")?.list()?:emptyList();rest.remove("name")
        val skin=si?.let {skins[it]};val primitives=mi?.let {meshes[it].objects("primitives")}?:emptyList()
        val selected=primitives.filterIndexed {pi,_->(mi to pi in hairRefs)==(ni in removed)}
        val binding=skin?.let {bindings(it,selected)}
        if(ni !in removed)rig[paths[ni]]=ho(*rest.entries.map {it.key to it.value}.toTypedArray(),"children" to children.filter {it.hi() !in removed}.map {paths[it.hi()]},"binding" to binding?.associate {val a=it.list();a[0].hs() to a[1]},"skeleton" to skin?.get("skeleton")?.let {paths[it.hi()]})
        else hairRig[paths[ni]]=ho(*rest.entries.map {it.key to it.value}.toTypedArray(),"children" to children.map {paths[it.hi()]},"parent" to if(parents[ni]<0)null else paths[parents[ni]],"binding" to binding)
        if(mi==null)continue
        for((pi,p) in primitives.withIndex()) {
            val key=paths[ni]+" / "+materials[p["material"].hi()]["name"].hs();require(!shapes.containsKey(key)){"Ambiguous primitive identity"}
            val vertices=accessor(p["indices"].hi());val attributes=p["attributes"].obj();val weights=accessor(attributes["WEIGHTS_0"].hi());val sk=checkNotNull(skin)
            val attrs=attributes.mapValues {(k,v)->values(v.hi(),vertices,sk,if(k=="JOINTS_0")weights else null)}
            val morph=p.optional("targets").map {it.obj().mapValues {(_,v)->values(v.hi(),vertices,sk)}}
            val meshSettings=hairCopy(meshes[mi]).obj().apply {remove("primitives")};val primitiveSettings=hairCopy(p).obj().apply {listOf("indices","attributes","targets","material").forEach(::remove)}
            val details=ho("attributes" to attrs,"morph" to morph,"primitiveBinding" to bindings(sk,listOf(p)),"material" to material(materials[p["material"].hi()]),"meshSettings" to meshSettings,"primitiveSettings" to primitiveSettings)
            shapes[key]=hairDigest(details)
            if(mi to pi in hairRefs)hairs.add(key)
        }
    }
    val extensions=hairCopy(json["extensions"]).obj();val vrm=extensions["VRMC_vrm"].obj();vrm.remove("meta")
    vrm["firstPerson"]?.obj()?.let {fp->if(fp.containsKey("meshAnnotations"))fp["meshAnnotations"]=fp.objects("meshAnnotations").filter {it["node"].hi() !in removed}}
    val spring=extensions["VRMC_springBone"]?.obj();val privateSprings=spring?.optional("springs")?.filterIndexed {i,_->i in ownership.springs}?:emptyList()
    spring?.set("springs",spring.optional("springs").filterIndexed {i,_->i !in ownership.springs})
    val root=hairCopy(json).obj().apply {(hairTables+listOf("nodes","meshes","skins","buffers","asset","extensions","scenes")).forEach(::remove)}
    val scenes=json.objects("scenes").map {s->hairCopy(s).obj().apply {this["nodes"]=s.optional("nodes").filter {it.hi() !in removed}.map {paths[it.hi()]}}}
    val world=hairDigest(ho("root" to root,"rig" to rig,"scenes" to scenes,"extensions" to refs(extensions)))
    val privateRig=hairDigest(ho("hairRig" to hairRig,"springs" to refs(privateSprings)))
    val ordered=shapes.toSortedMap().map {listOf(it.key,it.value)}
    return HairFingerprints(hairDigest(ho("world" to world,"shapes" to ordered.filter {it[0] !in hairs})),hairDigest(ho("rig" to privateRig,"shapes" to ordered.filter {it[0] in hairs})),world,shapes)
}
