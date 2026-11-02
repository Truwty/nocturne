package dev.truwty.nocturne.gui;
import dev.truwty.nocturne.setting.NumberSetting;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PreciseDragTest {
 @Test void slowMotionAccumulatesInsteadOfDisappearing() {
  var value=new NumberSetting("X",0,-10,10,.1,"");var drag=new PreciseDrag();
  for(int i=0;i<10;i++)drag.adjust(value,.01);
  assertEquals(.1,value.get(),.00001);
 }
 @Test void clampedDragReversesImmediatelyAndClearStartsANewGesture() {
  var value=new NumberSetting("X",9.9,-10,10,.1,"");var drag=new PreciseDrag();
  drag.adjust(value,100);drag.adjust(value,-.1);assertEquals(9.9,value.get(),.00001);
  drag.clear();value.set(0.0);drag.adjust(value,.1);assertEquals(.1,value.get(),.00001);
  assertThrows(IllegalArgumentException.class,()->drag.adjust(value,Double.NaN));
 }
}
