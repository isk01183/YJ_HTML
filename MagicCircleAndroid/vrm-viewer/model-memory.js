const MiB=1024*1024;
const memoryError=message=>Object.assign(new Error(message),{kind:'MEMORY'});

export function renderFrame(renderer,scene,camera,checkGpu=false) {
  try {
    renderer.render(scene,camera);
    if(checkGpu) {
      const gl=renderer.getContext();let failed=false;
      for(let i=0;i<32;i++) {
        const error=gl.getError();
        if(error===gl.NO_ERROR)break;
        if(error===gl.OUT_OF_MEMORY)throw memoryError('GPU texture allocation failed');
        failed=true;
      }
      if(failed)throw new Error('GPU upload failed');
    }
  } catch(error) {
    if(error.kind==='MEMORY')throw error;
    throw Object.assign(error,{kind:'CONTEXT'});
  }
}

export function imageDimensions(bytes,mime) {
  const view=new DataView(bytes.buffer,bytes.byteOffset,bytes.byteLength);
  let width,height;
  if(mime==='image/png' && bytes.length>=24 && view.getUint32(0)===0x89504e47 &&
      view.getUint32(4)===0x0d0a1a0a && view.getUint32(8)===13 && view.getUint32(12)===0x49484452) {
    width=view.getUint32(16);height=view.getUint32(20);
  } else if(mime==='image/jpeg' && bytes[0]===255 && bytes[1]===216) {
    let offset=2;
    while(offset+4<=bytes.length) {
      if(bytes[offset++]!==255)break;
      while(bytes[offset]===255)offset++;
      const marker=bytes[offset++];
      if(marker===217 || marker===218 || offset+2>bytes.length)break;
      if(marker===1 || (marker>=208 && marker<=215))continue;
      const length=view.getUint16(offset);
      if(length<2 || offset+length>bytes.length)break;
      if(marker>=192 && marker<=207 && ![196,200,204].includes(marker) && length>=8) {
        height=view.getUint16(offset+3);width=view.getUint16(offset+5);break;
      }
      offset+=length;
    }
  }
  if(!width || !height)throw new Error('Invalid embedded image dimensions');
  if(width>4096 || height>4096)throw memoryError('Texture exceeds 4096 pixels');
  return {width,height};
}

export class ModelMemory {
  constructor(budgetBytes,info) {
    Object.assign(info,{texturePixels:0,decodedImages:0,liveBitmaps:0,activeDecodes:0,peakDecodes:0,estimatedBytes:0,budgetBytes:budgetBytes || 0});
    if(!Number.isSafeInteger(budgetBytes) || budgetBytes<=0)throw memoryError('Available memory budget unavailable');
    this.info=info;this.bitmaps=new Set();this.closedBitmaps=new WeakSet();this.tail=Promise.resolve();
    this.closed=false;this.error=null;this.gpuBytes=0;
  }
  plugin(parser,maxTextureSize=4096) {
    if(!parser.textureLoader.isImageBitmapLoader)throw new Error('ImageBitmap support required');
    const load=parser.textureLoader.load.bind(parser.textureLoader);
    parser.textureLoader.load=(url,onLoad,onProgress,onError)=>{
      const work=this.tail.then(()=>{
        this.check();
        this.info.activeDecodes++;
        this.info.peakDecodes=Math.max(this.info.peakDecodes,this.info.activeDecodes);
        return new Promise((resolve,reject)=>{
          load(url,image=>{
            if(this.closed) {this.closeBitmap(image);reject(this.error || new Error('Viewer disposed'));return;}
            if(!this.bitmaps.has(image)) {this.bitmaps.add(image);this.info.decodedImages++;this.info.liveBitmaps=this.bitmaps.size;}
            resolve(image);
          },onProgress,reject);
        }).finally(()=>this.info.activeDecodes--);
      });
      this.tail=work.then(image=>onLoad(image)).catch(error=>{
        this.error ||= error;this.dispose();
        // GLTFLoader revokes embedded object URLs only on success.
        if(url.startsWith('blob:'))URL.revokeObjectURL(url);
        onError?.(this.error);
      });
    };
    return {name:'VRM_memory',beforeRoot:()=>this.admit(parser,maxTextureSize)};
  }
  admit(parser,maxTextureSize) {
    this.check();
    const {json}=parser,body=parser.extensions.KHR_binary_glTF?.body;
    if(json.buffers?.length!==1 || json.buffers[0].uri || !(body instanceof ArrayBuffer))throw new Error('Embedded GLB buffer required');
    const pixels=(json.images || []).map(image=>{
      const view=json.bufferViews?.[image.bufferView];
      if(image.uri || !view || view.buffer!==0)throw new Error('Embedded image required');
      const {width,height}=imageDimensions(new Uint8Array(body,view.byteOffset || 0,view.byteLength),image.mimeType);
      if(width>maxTextureSize || height>maxTextureSize)throw new Error('Original texture exceeds the GPU size limit');
      return width*height;
    });
    const components={SCALAR:1,VEC2:2,VEC3:3,VEC4:4,MAT2:4,MAT3:9,MAT4:16};
    const geometryBytes=(json.accessors || []).reduce((sum,accessor)=>sum+accessor.count*components[accessor.type]*4,0);
    const viewBytes=(json.bufferViews || []).reduce((sum,view)=>sum+view.byteLength,0);
    const texturePixels=(json.textures || []).reduce((sum,texture)=>sum+pixels[texture.source],0);
    this.info.texturePixels=pixels.reduce((sum,value)=>sum+value,0);
    this.gpuBytes=Math.ceil(texturePixels*16/3);
    // Overlapping views still allocate separate slices; reserve them in addition to GLB/blobs and decoded resources.
    this.info.estimatedBytes=4*json.buffers[0].byteLength+viewBytes+4*geometryBytes+4*this.info.texturePixels+
      this.gpuBytes+4*Math.max(0,...pixels)+32*MiB;
    if(this.info.texturePixels>64*MiB)throw memoryError('Total texture pixels exceed the model limit');
    this.check();
  }
  check(gltf) {
    if(this.error)throw this.error;
    if(this.closed)throw new Error('Viewer disposed');
    if(gltf) {
      const sources=new Map();let gpuPixels=0;
      const account=texture=>{
        if(!texture?.isTexture)return;
        const keys=sources.get(texture.source) || new Set();sources.set(texture.source,keys);
        const key=[texture.wrapS,texture.wrapT,texture.wrapR || 0,texture.magFilter,texture.minFilter,
          texture.anisotropy,texture.internalFormat,texture.format,texture.type,texture.generateMipmaps,
          texture.premultiplyAlpha,texture.flipY,texture.unpackAlignment,texture.colorSpace].join();
        if(!keys.has(key)) {keys.add(key);gpuPixels+=(texture.image?.width || 0)*(texture.image?.height || 0);}
      };
      for(const scene of gltf.scenes)scene.traverse(object=>{
        for(const material of Array.isArray(object.material)?object.material:[object.material]) {
          if(!material)continue;
          Object.values(material).forEach(account);
          Object.values(material.uniforms || {}).forEach(uniform=>{
            if(Array.isArray(uniform.value))uniform.value.forEach(account);else account(uniform.value);
          });
        }
      });
      const actualGpuBytes=Math.ceil(gpuPixels*16/3);
      if(actualGpuBytes>this.gpuBytes) {this.info.estimatedBytes+=actualGpuBytes-this.gpuBytes;this.gpuBytes=actualGpuBytes;}
    }
    if(!Number.isSafeInteger(this.info.estimatedBytes) || this.info.estimatedBytes>this.info.budgetBytes) {
      throw memoryError('Original textures exceed the available memory budget');
    }
  }
  closeBitmap(image) {
    if(!this.closedBitmaps.has(image)) {this.closedBitmaps.add(image);image.close();}
  }
  dispose() {
    this.closed=true;
    for(const image of this.bitmaps)this.closeBitmap(image);
    this.bitmaps.clear();this.info.liveBitmaps=0;
  }
}
