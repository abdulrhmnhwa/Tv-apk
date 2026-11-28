/* محرك حساب المواقيت (نقل JS لحساب مشروع Open Prayer Times). يحتاج Astronomy */
(function(root){
var A=root.Astronomy||(typeof require!=='undefined'?require('astronomy-engine'):null);
var D2R=Math.PI/180, J2000=2451545.0;
function mkEng(c){
  var P=(c.P&&c.P>0)?c.P:1010,T=(c.T===0||c.T)?c.T:10,lat=c.lat,lon=c.lon;
  var obs=new A.Observer(lat,lon,0),la=lat*D2R;
  function unLT(aa){var d=aa/D2R,a=((2e-5*d+1.96e-2)*d+1.594e-1)*P,b=(273+T)*((8.45e-2*d+5.05e-1)*d+1),r=(a/b)*D2R;return(aa<0&&r<0)?aa:aa-r;}
  function unGE(aa){return aa-7.888888e-5*P/((273+T)*Math.tan(aa));}
  function unref(aa){var d=aa/D2R;if(d<14.5)return unLT(aa);if(d>=15.5)return unGE(aa);var p=d-14.5,x=unLT(aa),y=unGE(aa);return x+(y-x)*p;}
  function refract(ta){var t=unref(ta),d=0.8*(ta-t),t0=t,a=ta;
    for(var i=0;i<50;i++){a+=d;t=unref(a);if(Math.abs(ta-t)<=0.1/3600*D2R)break;d*=-(ta-t)/(t0-t);t0=t;}return a;}
  var cache={};
  function st(j){var k=j.toFixed(7);if(cache[k])return cache[k];
    var t=new A.AstroTime(j-J2000),eq=A.Equator(A.Body.Sun,t,obs,true,true);
    var ra=eq.ra*15*D2R,dec=eq.dec*D2R,g=A.SiderealTime(t)*15*D2R;
    var H=g+lon*D2R-ra,alt=Math.asin(Math.sin(la)*Math.sin(dec)+Math.cos(la)*Math.cos(dec)*Math.cos(H));
    var sd=Math.asin(695700/(eq.dist*149597870.7));
    if(Object.keys(cache).length>4000)cache={};
    return cache[k]={alt:alt,sd:sd,H:H,dec:dec};}
  function wrap(x){x=x%(2*Math.PI);if(x>Math.PI)x-=2*Math.PI;if(x<-Math.PI)x+=2*Math.PI;return x;}
  // g(j)= apparent altitude - target ; target horizon deg, limb bool
  function mkg(h,limb){var hr=h*D2R;return function(j){var s=st(j);return refract(s.alt)-(hr-(limb?s.sd:0));};}
  function root_(g,lo,hi){var f0=g(lo),f1=g(hi);if(!(f0<0&&f1>0)&&!(f0>0&&f1<0))return null;
    var side=0;for(var i=0;i<80;i++){var m=(lo*f1-hi*f0)/(f1-f0),fm=g(m);
      if(fm===0)return m;
      if((fm>0)===(f1>0)){hi=m;f1=fm;if(side===-1)f0/=2;side=-1;}else{lo=m;f0=fm;if(side===1)f1/=2;side=1;}
      if(Math.abs(hi-lo)<1e-9)break;}
    return (lo+hi)/2;}
  function transit(j0){var j=j0+0.5;for(var i=0;i<8;i++){var H=wrap(st(j).H);j-=H/(2*Math.PI)/1.0027379;}
    if(j<j0){j+=1.0027379;for(var i2=0;i2<4;i2++){var H2=wrap(st(j).H);j-=H2/(2*Math.PI)/1.0027379;}}return j;}
  function rising(j0,Tr,h,limb){var g=mkg(h,limb);return root_(g,Tr-0.5,Tr);}
  function setting(Tr,h,limb){var g=mkg(h,limb);return root_(g,Tr,Tr+0.5);}
  return {st:st,mkg:mkg,root:root_,transit:transit,rising:rising,setting:setting,refract:refract};
}
function pad(n){return n<10?'0'+n:''+n;}
var tzc={};
function tzOffMin(tz,ms){var f=tzc[tz]||(tzc[tz]=new Intl.DateTimeFormat('en-US',{timeZone:tz,hourCycle:'h23',year:'numeric',month:'numeric',day:'numeric',hour:'numeric',minute:'numeric',second:'numeric'}));
  var p={};f.formatToParts(new Date(ms)).forEach(function(x){p[x.type]=+x.value;});
  var asUtc=Date.UTC(p.year,p.month-1,p.day,p.hour%24,p.minute,p.second);return Math.round((asUtc-Math.floor(ms/1000)*1000)/60000);}
/* c: {lat,lon,tz,el,P,T,fa,ia,im,mad,off:[f,s,d,a,m,i]}
   returns {t:[6 local minutes of day (0..1439)], ok:true} or null if a prayer cannot be computed */
function calc(c,y,m,d){
  var E=mkEng(c),dip=1.76*Math.sqrt(Math.max(c.el||0,0))/60;
  var j0=Date.UTC(y,m-1,d)/86400000+2440587.5;
  var Tr=E.transit(j0);
  var sr=E.rising(j0,Tr,dip,true),ms=E.setting(Tr,dip,true);
  var isha,fajr;
  if(c.im>0){isha=ms==null?null:ms+c.im/1440;}
  else{
    var iv=null;if(ms!=null){var gi=E.mkg(-Math.abs(c.ia)+dip,false);iv=E.root(gi,ms,Tr+0.5);}
    isha=iv;
  }
  var gf=E.mkg(-Math.abs(c.fa),false),fv=E.root(gf,Tr-0.5,Tr);
  fajr=fv;
  // Asr
  var s=E.st(Tr),diff=Math.abs(la_(c.lat)-s.dec);
  var alt=Math.atan(1/(((c.mad===1)?2:1)+Math.tan(diff)))/D2R;
  var asr=E.root(E.mkg(alt,false),Tr,Tr+0.5);
  var raw=[fajr,sr,Tr,asr,ms,isha];
  var out=[];
  for(var i=0;i<6;i++){
    if(raw[i]==null)return null;
    var ms_=Math.round((raw[i]-2440587.5)*86400000);
    ms_+=Math.round(((c.off&&c.off[i])||0)*60000);
    var sec=Math.floor(ms_/1000);var m1=Math.floor(sec/60),s1=sec-m1*60; if(s1>=30)m1++;
    var tms=m1*60000;var off=tzOffMin(c.tz,tms);
    var lm=((m1+off)%1440+1440)%1440;out.push(lm);
  }
  return out;
}
function la_(x){return x*D2R;}
var API={calc:calc,tzOffMin:tzOffMin};
if(typeof module!=='undefined')module.exports=API;else root.PTENG=API;
})(typeof window!=='undefined'?window:globalThis);
