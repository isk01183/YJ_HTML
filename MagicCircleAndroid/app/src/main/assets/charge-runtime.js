/* Native owns the deadline. Loading never starts playback; infinite ornament loops keep their speed. */
(()=>{
 const root=document.documentElement, custom=new URLSearchParams(location.search).get('editableInfo')==='1';
 if(custom) {
  root.classList.add('editable-info');
  const style=document.createElement('style');
  style.textContent='.editable-info .level,.editable-info .percentage,.editable-info .state,.editable-info .metrics,.editable-info .meter,.editable-info .caption,.editable-info [data-i18n="begin"],.editable-info .connect h1,.editable-info .connect p,.editable-info .begin h2,.editable-info .begin p,.editable-info .phase,.editable-info .complete h2,.editable-info .complete p,.editable-info .finish h2,.editable-info .finish p{visibility:hidden!important}';
  document.head.appendChild(style);
 }
 window.prepareChargingAnimation ||= ()=>true;
 const original=window.startChargingAnimation;
 window.startChargingAnimation=(remaining=7000)=>{
  if(!window.prepareChargingAnimation() || remaining<=0)return false;
  const result=original(remaining);
  document.getAnimations().forEach(a=>{if(a.effect.getTiming().iterations===Infinity)a.playbackRate=1});
  return result;
 };
 window.showChargeEditorFrame=()=>{
  if(!window.startChargingAnimation(7000))return false;
  document.getAnimations().forEach(a=>{a.pause();a.currentTime=4000});
  return true;
 };
})();
