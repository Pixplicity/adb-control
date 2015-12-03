package com.pixplicity.adb;

import java.util.ArrayList;

public class ProcessInfo {
    private ArrayList<PsRow> pslist;
    private static String rootpid = null;

    public boolean execute() {
        String ps = RunScript.runIt("ps");
        if (ps == null) {
            return false;
        }
        String[] lines = ps.split("\n");
        pslist = new ArrayList<PsRow>();
        for (String line : lines) {
            // Log.v(line);
            PsRow row = new PsRow(line);
            if (row.pid != null) {
                pslist.add(row);
            }
        }
        return true;
    }

    public PsRow getPsRow(String cmd) {
        for (PsRow row : pslist) {
            if (cmd.equals(row.cmd)) {
                return row;
            }
        }
        return null;
    }

    public static class PsRow {
        String pid = null;
        String cmd;
        String ppid;
        String user;
        int mem;

        public PsRow(String line) {
            if (line == null) {
                return;
            }
            String[] p = line.split("[\\s]+");
            if (p.length != 9) {
                return;
            }
            user = p[0];
            pid = p[1];
            ppid = p[2];
            cmd = p[8];
            mem = StrUtil.parseInt(p[4]);
            if (isRoot()) {
                rootpid = pid;
            }
        }

        public boolean isRoot() {
            return "zygote".equals(cmd);
        }

        public boolean isMain() {
            return ppid.equals(rootpid) && user.startsWith("app_");
        }

        /**
         * Constructs a <code>String</code> with all attributes in name = value
         * format.
         *
         * @return a <code>String</code> representation of this object.
         */
        @Override
        public String toString() {
            final String TAB = ";";

            String retValue = "";

            retValue = "PsRow ( " + super.toString() + TAB + "pid = "
                    + this.pid + TAB + "cmd = " + this.cmd
                    + TAB + "ppid = " + this.ppid + TAB + "user = " + this.user
                    + TAB + "mem = " + this.mem
                    + " )";

            return retValue;
        }

    }

}
