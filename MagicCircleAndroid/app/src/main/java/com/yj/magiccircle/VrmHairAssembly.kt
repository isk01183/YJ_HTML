package com.yj.magiccircle

import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import android.system.Os

object VrmHairAssembly {
    private val verifiedFingerprints=linkedMapOf<String,HairFingerprints>()
    private fun fingerprint(document: HairDocument,id: String): HairFingerprints {
        require(hairHash(document.file)==id)
        return verifiedFingerprints[id] ?: document.fingerprints().also {
            if(verifiedFingerprints.size>=8)verifiedFingerprints.remove(verifiedFingerprints.keys.first())
            verifiedFingerprints[id]=it
        }
    }
    @Synchronized fun extract(base: File,source: File,outputDirectory: File,budgetBytes: Long): VrmHairPart {
        require(budgetBytes>=32L*1024*1024&&!outputDirectory.exists())
        val baseId=hairHash(base);val sourceId=hairHash(source)
        HairDocument.open(base,budgetBytes/2).use {b->HairDocument.open(source,budgetBytes/2).use {s->
            b.validateGeometry();s.validateGeometry();val before=fingerprint(b,baseId);val after=fingerprint(s,sourceId);require(before.protectedDigest==after.protectedDigest){"Face/body/outfit/rig differs"}
            val ownership=s.ownership();val local=(ownership.nodes+ownership.meshNodes).sorted();val anchors=sortedSetOf<String>()
            fun ref(i: Int): Any? {if(i<0)return null;val n=local.indexOf(i);if(n>=0)return ho("local" to n);require(s.paths[i] in b.paths);anchors.add(s.paths[i]);return ho("anchor" to s.paths[i])}
            val doc=ho("schemaVersion" to 1,"kind" to "hair","styleId" to if(before.hairDigest==after.hairDigest)"e-original" else "e-hair02","baseModelId" to baseId,"sourceModelId" to sourceId,
                "protectedDigest" to after.protectedDigest,"hairDigest" to after.hairDigest)
            doc["nodes"]=local.map {i->ho("node" to hairCopy(s.nodes[i]).obj().apply {listOf("children","mesh","skin").forEach(::remove)},"parent" to ref(s.parents[i]))}
            val parent=checkNotNull(outputDirectory.absoluteFile.parentFile);require(parent.isDirectory||parent.mkdirs())
            val temp=File(parent,".hair-${UUID.randomUUID()}");check(temp.mkdir())
            try {
                HairWriter(File(temp,"part.bin"),doc).use {w->
                    val geometry=mutableListOf<HairObject>()
                    for((ni,node) in s.nodes.withIndex())if(node.containsKey("mesh")) {
                        val mi=node["mesh"].hi();val original=s.meshes[mi];val ps=original.objects("primitives").filterIndexed {i,_->mi to i in s.hairRefs};if(ps.isEmpty())continue
                        val skin=s.skins[node["skin"].hi()];val used=s.used(ps);val matrix=s.accessor(skin["inverseBindMatrices"].hi());val mesh=hairCopy(original).obj()
                        mesh["primitives"]=ps.map {w.primitive(s,it){joint->used.indexOf(joint)}}
                        val outputSkin=ho("joints" to used.map {ref(skin["joints"].list()[it].hi())},"inverseBindMatrices" to w.accessor(used.flatMap {matrix.copyOfRange(it*16,it*16+16).toList()}.toDoubleArray(),"MAT4",5126))
                        if(skin["skeleton"]!=null)outputSkin["skeleton"]=ref(skin["skeleton"].hi())
                        geometry.add(ho("target" to ref(ni),"mesh" to mesh,"skin" to outputSkin))
                    }
                    doc["geometry"]=geometry
                    val spring=s.json["extensions"].obj()["VRMC_springBone"]?.obj()
                    doc["springs"]=(spring?.optional("springs")?:emptyList()).filterIndexed {i,_->i in ownership.springs}.map {v->hairCopy(v).obj().apply {
                        this["joints"]=objects("joints").map {j->hairCopy(j).obj().apply {this["node"]=ref(j["node"].hi())}}
                        if(this["center"]!=null)this["center"]=ref(this["center"].hi())
                    }}
                    doc["annotations"]=(s.json["extensions"].obj()["VRMC_vrm"].obj()["firstPerson"]?.obj()?.optional("meshAnnotations")?:emptyList()).map {it.obj()}.filter {it["node"].hi() in ownership.meshNodes}.map {a->hairCopy(a).obj().apply {this["node"]=ref(a["node"].hi())}}
                    doc["anchors"]=anchors.toList();w.finish();doc["binLength"]=w.size;doc["binSha256"]=hairHash(w.file)
                }
                writeJson(File(temp,"part.json"),doc)
                val part=VrmHairPartCodec.load(temp,budgetBytes).use {it.part}
                b.close();s.close()
                val proof=File(parent,".proof-${UUID.randomUUID()}.vrm")
                try {compose(base,part,proof,budgetBytes)}finally {proof.delete()}
                require(hairHash(base)==baseId&&hairHash(source)==sourceId){"Source changed during extraction"}
                require(!outputDirectory.exists());Os.rename(temp.path,outputDirectory.path)
                return part.copy(directory=outputDirectory)
            } finally {File(temp,"part.json").delete();File(temp,"part.bin").delete();temp.delete()}
        }}
    }

    @Synchronized fun compose(base: File,part: VrmHairPart,output: File,budgetBytes: Long): VrmHairAssemblyResult {
        require(budgetBytes>=32L*1024*1024&&!output.exists())
        VrmHairPartCodec.load(part.directory,budgetBytes/2).use {loaded->
            require(loaded.part==part){"Part identity changed"};val d=loaded.json;require(hairHash(base)==part.baseModelId)
            HairDocument.open(base,budgetBytes/2).use {b->
                b.validateGeometry();val expected=fingerprint(b,part.baseModelId);require(expected.protectedDigest==d["protectedDigest"])
                val owned=b.ownership();val removed=owned.nodes+owned.meshNodes;val j=hairCopy(b.json).obj();val baseMap=mutableMapOf<Int,Int>();val nodes=mutableListOf<HairObject>()
                b.nodes.forEachIndexed {i,n->if(i !in removed){baseMap[i]=nodes.size;nodes.add(hairCopy(n).obj())}}
                val localStart=nodes.size;val local=d.objects("nodes");nodes.addAll(local.map {hairCopy(it["node"]).obj()})
                fun bn(i: Int)=checkNotNull(baseMap[i]){"Protected reference points to hair"}
                fun ref(value: Any?): Int {val r=value.obj();return if(r.containsKey("local"))localStart+r["local"].hi() else bn(b.paths.indexOf(r["anchor"].hs()))}
                for((_,ni) in baseMap){val n=nodes[ni];if(n.containsKey("children"))n["children"]=n.optional("children").filter {it.hi() in baseMap}.map {bn(it.hi())}}
                for((i,n) in local.withIndex())if(n["parent"]!=null){val parent=nodes[ref(n["parent"])];parent["children"]=parent.optional("children")+(localStart+i)}
                j["nodes"]=nodes;j["meshes"]=mutableListOf<HairObject>();j["skins"]=mutableListOf<HairObject>()
                j["scenes"]=b.json.objects("scenes").map {scene->hairCopy(scene).obj().apply {this["nodes"]=scene.optional("nodes").filter {it.hi() in baseMap}.map {bn(it.hi())}+local.mapIndexedNotNull {i,n->if(n["parent"]==null)localStart+i else null}}}
                val parent=checkNotNull(output.absoluteFile.parentFile);require(parent.isDirectory||parent.mkdirs())
                val bin=File(parent,".hair-bin-${UUID.randomUUID()}");val temp=File(parent,".hair-output-${UUID.randomUUID()}")
                try {
                    HairWriter(bin,j).use {w->
                        val geometry=d.objects("geometry").associateBy {ref(it["target"])};val origins=baseMap.entries.associate {it.value to it.key}
                        val meshes=mutableListOf<HairObject>();val skins=mutableListOf<HairObject>();j["meshes"]=meshes;j["skins"]=skins
                        for(ni in nodes.indices) {
                            val sourceNode=origins[ni]?.let {b.nodes[it]};val g=geometry[ni];val mi=sourceNode?.get("mesh")?.hi();if(mi==null&&g==null)continue
                            val bm=mi?.let {b.meshes[it]};val ps=bm?.objects("primitives")?.filterIndexed {i,_->mi to i !in b.hairRefs}?:emptyList();val skin=sourceNode?.get("skin")?.hi()?.let {b.skins[it]}
                            val joints=mutableListOf<Int>();val matrices=mutableListOf<Double>();val baseJoints=mutableMapOf<Int,Int>();val partJoints=mutableMapOf<Int,Int>()
                            fun add(n: Int,m: DoubleArray): Int {val old=joints.indexOf(n);if(old>=0){require(matrices.subList(old*16,old*16+16)==m.toList()){"Conflicting bind matrix"};return old};joints.add(n);matrices.addAll(m.toList());return joints.lastIndex}
                            if(ps.isNotEmpty()){val sk=checkNotNull(skin);val matrix=b.accessor(sk["inverseBindMatrices"].hi());for(i in b.used(ps))baseJoints[i]=add(bn(sk["joints"].list()[i].hi()),matrix.copyOfRange(i*16,i*16+16))}
                            if(g!=null){val sk=g["skin"].obj();val matrix=loaded.source.accessor(sk["inverseBindMatrices"].hi());sk["joints"].list().forEachIndexed {i,r->partJoints[i]=add(ref(r),matrix.copyOfRange(i*16,i*16+16))}}
                            val baseSkeleton=skin?.get("skeleton")?.let {bn(it.hi())};val partSkeleton=g?.get("skin")?.obj()?.get("skeleton")?.let(::ref)
                            if(baseSkeleton!=null&&partSkeleton!=null)require(baseSkeleton==partSkeleton)
                            val resultSkin=ho("joints" to joints,"inverseBindMatrices" to w.accessor(matrices.toDoubleArray(),"MAT4",5126));(baseSkeleton?:partSkeleton)?.let {resultSkin["skeleton"]=it}
                            nodes[ni]["skin"]=skins.size;skins.add(resultSkin)
                            val mesh=hairCopy(bm?:g!!["mesh"]).obj();mesh["primitives"]=ps.map {p->w.primitive(b,p){checkNotNull(baseJoints[it])}}+(g?.get("mesh")?.obj()?.objects("primitives")?:emptyList()).map {p->w.primitive(loaded.source,p){checkNotNull(partJoints[it])}}
                            nodes[ni]["mesh"]=meshes.size;meshes.add(mesh)
                        }
                        val originalVrm=hairCopy(b.json["extensions"].obj()["VRMC_vrm"]).obj()
                        originalVrm["firstPerson"]?.obj()?.let {fp->if(fp.containsKey("meshAnnotations"))fp["meshAnnotations"]=fp.objects("meshAnnotations").filter {it["node"].hi() in baseMap}}
                        val vrm=nodeRefs(originalVrm){bn(it.hi())}.obj();j["extensions"].obj()["VRMC_vrm"]=vrm
                        if(d.objects("annotations").isNotEmpty()){val fp=vrm["firstPerson"]?.obj()?:ho("meshAnnotations" to emptyList<Any?>()).also {vrm["firstPerson"]=it};fp["meshAnnotations"]=fp.optional("meshAnnotations")+d.objects("annotations").map {a->hairCopy(a).obj().apply {this["node"]=ref(a["node"])}}}
                        val expr=vrm["expressions"]?.obj()?:ho()
                        for(e in (expr["preset"]?.obj()?.values?:emptyList())+(expr["custom"]?.obj()?.values?:emptyList()))for(v in e.obj().optional("materialColorBinds")+e.obj().optional("textureTransformBinds")){val bind=v.obj();bind["material"]=w.resource(b,"materials",bind["material"].hi())}
                        vrm["meta"]?.obj()?.let {meta->meta["thumbnailImage"]?.let {meta["thumbnailImage"]=w.resource(b,"images",it.hi())}}
                        val bs=b.json["extensions"].obj()["VRMC_springBone"]?.obj()
                        if(bs!=null||d.objects("springs").isNotEmpty()) {
                            val preserved=hairCopy(bs?:ho("specVersion" to "1.0","springs" to emptyList<Any>())).obj();preserved["springs"]=preserved.optional("springs").filterIndexed {i,_->i !in owned.springs}
                            val spring=nodeRefs(preserved){bn(it.hi())}.obj();spring["springs"]=spring.optional("springs")+d.objects("springs").map {s->hairCopy(s).obj().apply {this["joints"]=s.objects("joints").map {p->hairCopy(p).obj().apply {this["node"]=ref(p["node"])}};if(s["center"]!=null)this["center"]=ref(s["center"])}}
                            j["extensions"].obj()["VRMC_springBone"]=spring
                        }
                        w.finish();j["buffers"]=listOf(ho("byteLength" to w.size));pack(j,bin,temp)
                    }
                    HairDocument.open(temp,budgetBytes/2).use {result->result.validateGeometry();val fp=fingerprint(result,hairHash(temp));require(fp.protectedDigest==d["protectedDigest"]&&fp.hairDigest==d["hairDigest"]){"Composed appearance changed"}}
                    require(hairHash(base)==part.baseModelId&&VrmHairPartCodec.read(part.directory)==part){"Input changed during composition"}
                    // Android app sandboxes can prohibit hard links; all publishers share this monitor.
                    val id=hairHash(temp);hairCheckInterrupt();require(!output.exists());Os.rename(temp.path,output.path)
                    return VrmHairAssemblyResult(output,id,part.id,d["protectedDigest"].hs(),d["hairDigest"].hs())
                } finally {bin.delete();temp.delete()}
            }
        }
    }

    private fun nodeRefs(v: Any?,resolve: (Any?)->Int): Any?=when(v) {
        is Map<*,*>->v.entries.associateTo(linkedMapOf<String,Any?>()){val k=it.key as String;k to if(k in listOf("node","center"))resolve(it.value)else nodeRefs(it.value,resolve)}
        is List<*>->v.map {nodeRefs(it,resolve)};else->v
    }
    private fun writeJson(file: File,json: HairObject) {val bytes=hairJson(json,false).toByteArray(Charsets.UTF_8);require(bytes.size<=4*1024*1024);FileOutputStream(file).use {it.write(bytes);it.fd.sync()}}
    private fun pack(json: HairObject,bin: File,out: File) {
        val bytes=hairJson(json,false).toByteArray(Charsets.UTF_8);val padded=(bytes.size+3)/4*4;val total=28L+padded+bin.length();require(padded<=4*1024*1024&&total<=VrmModelStore.MAX_BYTES)
        FileOutputStream(out).use {stream->
            fun int(v: Int){stream.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array())}
            int(0x46546c67);int(2);int(total.toInt());int(padded);int(0x4e4f534a);stream.write(bytes);repeat(padded-bytes.size){stream.write(32)};int(bin.length().toInt());int(0x004e4942)
            bin.inputStream().use {input->val buffer=ByteArray(32768);while(true){hairCheckInterrupt();val n=input.read(buffer);if(n<0)break;stream.write(buffer,0,n)}};stream.fd.sync()
        }
    }
}

private class HairWriter(val file: File,private val json: HairObject): java.io.Closeable {
    private val output=FileOutputStream(file);var size=0L;private set
    private val resources=mutableMapOf<HairDocument,MutableMap<String,Int>>();private val images=mutableMapOf<String,Int>();private var finished=false
    init {hairTables.forEach {json[it]=mutableListOf<HairObject>()}}
    @Suppress("UNCHECKED_CAST") private fun table(key: String)=json[key] as MutableList<HairObject>
    private fun view(length: Int,write: ()->Unit): Int {require(length>0&&size+length<=VrmModelStore.MAX_BYTES);val table=table("bufferViews");val i=table.size;table.add(ho("buffer" to 0,"byteOffset" to size,"byteLength" to length));write();size+=length;while(size%4!=0L){output.write(0);size++};return i}
    fun accessor(values: DoubleArray,type: String,component: Int): Int {
        val width=hairWidths.getValue(type);require(values.isNotEmpty()&&values.size%width==0);val bytes=if(component==5123)2 else 4
        val index=view(values.size*bytes){val buffer=ByteBuffer.allocate(32768).order(ByteOrder.LITTLE_ENDIAN)
            for(v in values){require(v.isFinite());if(buffer.remaining()<bytes){output.write(buffer.array(),0,buffer.position());buffer.clear();hairCheckInterrupt()}
                when(component){5126->buffer.putFloat(v.toFloat());5123->{require(v==v.toInt().toDouble()&&v in 0.0..65535.0);buffer.putShort(v.toInt().toShort())};5125->{require(v==v.toLong().toDouble()&&v in 0.0..4294967295.0);buffer.putInt(v.toLong().toInt())};else->error("Unsupported component")}}
            if(buffer.position()>0)output.write(buffer.array(),0,buffer.position())}
        val table=table("accessors");val i=table.size;table.add(ho("bufferView" to index,"type" to type,"componentType" to component,"count" to values.size/width));return i
    }
    fun resource(source: HairDocument,kind: String,index: Int): Int {
        val cache=resources.getOrPut(source){mutableMapOf()};val key="$kind:$index";cache[key]?.let {return it};val value=hairCopy(source.json.objects(kind)[index]).obj()
        if(kind=="images") {
            val hash=value["mimeType"].hs()+source.imageHash(index);images[hash]?.let {cache[key]=it;return it}
            val v=source.json.objects("bufferViews")[value["bufferView"].hi()];value["bufferView"]=view(v["byteLength"].hi()){source.copySpan((v["byteOffset"]?:0).hi().toLong(),v["byteLength"].hi().toLong()){b,n->output.write(b,0,n)}};images[hash]=table("images").size
        }
        if(kind=="textures"){value["source"]=resource(source,"images",value["source"].hi());if(value["sampler"]!=null)value["sampler"]=resource(source,"samplers",value["sampler"].hi())}
        if(kind=="materials") {
            fun walk(v: Any?) {if(v is Map<*,*>)for((k,item) in v){if((k as String).endsWith("Texture")&&item is Map<*,*>&&item.containsKey("index")){val t=item.obj();t["index"]=resource(source,"textures",t["index"].hi())}else walk(item)}else if(v is List<*>)v.forEach(::walk)};walk(value)
        }
        val out=table(kind).size;table(kind).add(value);cache[key]=out;return out
    }
    fun primitive(source: HairDocument,p: HairObject,joint: (Int)->Int): HairObject {
        val indices=source.accessor(p["indices"].hi());val vertices=indices.toSet().toList();val map=vertices.mapIndexed {i,v->v to i}.toMap();val attrs=p["attributes"].obj();val weights=source.accessor(attrs["WEIGHTS_0"].hi())
        fun subset(index: Int,key: String): Int {
            val a=source.json.objects("accessors")[index];val data=source.accessor(index);val width=hairWidths.getValue(a["type"].hs());val values=DoubleArray(vertices.size*width)
            for(i in vertices.indices)for(k in 0 until width){val v=vertices[i].hi();values[i*width+k]=if(key=="JOINTS_0"){if(weights[v*4+k]==0.0)0.0 else joint(data[v*width+k].hi()).toDouble()}else data[v*width+k]}
            return accessor(values,a["type"].hs(),a["componentType"].hi())
        }
        return hairCopy(p).obj().apply {
            this["material"]=resource(source,"materials",p["material"].hi());this["attributes"]=attrs.mapValues {(k,v)->subset(v.hi(),k)}
            this["indices"]=accessor(DoubleArray(indices.size){map.getValue(indices[it]).toDouble()},"SCALAR",if(vertices.size>65535)5125 else 5123)
            if(p.containsKey("targets"))this["targets"]=p.optional("targets").map {t->t.obj().mapValues {(k,v)->subset(v.hi(),k)}}
        }
    }
    fun finish(){if(!finished){output.fd.sync();output.close();finished=true}}
    override fun close(){if(!finished){output.close();finished=true}}
}
