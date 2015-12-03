package com.pixplicity.adb.widget;

import com.pixplicity.adb.R;

public class AdbWidget2 extends AdbWidget {

    @Override
    protected int getLayout() {
        return R.layout.widget2_provider;
    }

    @Override
    protected int[] getButtons() {
        return new int[] {
                R.id.button1,
                R.id.button2
        };
    }
}
