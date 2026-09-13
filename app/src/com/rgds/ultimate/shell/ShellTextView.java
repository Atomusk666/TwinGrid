package com.rgds.ultimate.shell;
import android.content.Context;
import android.widget.TextView;
final class ShellTextView extends TextView {
    ShellTextView(Context c){super(c);}
    @Override public void setText(CharSequence value,BufferType type){CharSequence next=UiStrings.bind(this,value);if(!next.toString().contentEquals(getText()==null?"":getText()))super.setText(next,type);}
}
