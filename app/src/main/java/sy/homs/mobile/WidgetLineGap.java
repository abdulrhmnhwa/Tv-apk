package sy.homs.mobile;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import android.widget.*;
import android.util.TypedValue;

/** Matches the visible text gap, including each font's unused ascent/descent. */
final class WidgetLineGap {
 static int[] ink(Context c,String text,float dp,boolean bold) {
  TextView t=new TextView(c);t.setIncludeFontPadding(false);t.setMaxLines(1);
  t.setTextSize(TypedValue.COMPLEX_UNIT_DIP,dp);t.setTextColor(Color.WHITE);
  if(bold)t.setTypeface(null,Typeface.BOLD);t.setText(text);
  t.measure(View.MeasureSpec.makeMeasureSpec(4096,View.MeasureSpec.AT_MOST),0);
  int w=Math.max(1,t.getMeasuredWidth()),h=Math.max(1,t.getMeasuredHeight());t.layout(0,0,w,h);
  Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);t.draw(new Canvas(b));
  int first=h,last=0;int[] px=new int[w*h];b.getPixels(px,0,w,0,0,w,h);b.recycle();
  for(int y=0;y<h;y++)for(int x=0;x<w;x++)if((px[y*w+x]>>>24)>64){first=Math.min(first,y);last=Math.max(last,y);}
  return new int[]{h,first,last};
 }
 static int prayerPadding(Context c,float k,String name,String time,String label,float atScale,float cntScale) {
  int[] n=ink(c,name,30*k,true),a=ink(c,time,14*k*atScale,false);
  int[] l=ink(c,label,12*k*cntScale,false),d=ink(c,"2:19:37",28*k*cntScale,true);
  int countGap=l[0]-l[2]-1+d[1];
  int prayerGap=n[0]-n[2]-1+a[1];
  float density=c.getResources().getDisplayMetrics().density;
  return Math.max(0,Math.round(20*density)+countGap-prayerGap);
 }
}
