package com.pixplicity.adb.widget;

import com.pixplicity.adb.R;

public class AdbWidget1 extends AdbWidget {

    @Override
    protected int getLayout() {
        return R.layout.widget1_provider;
    }

    @Override
    protected int[] getButtons() {
        return new int[] {
                R.id.button1
        };
    }

}
