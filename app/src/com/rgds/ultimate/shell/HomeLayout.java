package com.rgds.ultimate.shell;

/** HOME-only physical pixels. Library and tool pages keep DsGrid's existing geometry. */
final class HomeLayout {
    static final DsGrid.Spec TOP_GRID=new DsGrid.Spec(true,0,0,40,4,195);
    static final DsGrid.Spec BOTTOM_GRID=new DsGrid.Spec(false,80,60,120,4,170);
    static final int[] CLOCK={40,120,240,240},MONTH={320,120,280,240},MONTH_TITLE={320,80,280,40};
    static final int[][] ENTRIES={{80,64,480,112},{80,184,232,112},{328,184,232,112},{80,304,480,112},{336,428,48,48},{256,428,48,48}};
    // Stable identities: slot, all, favorites, recent, settings, tasks.
    static int navigate(int index,int direction){
        // The bottom pair reads left to right: tasks, settings. Down enters its first tool.
        int[][] next={{0,1,0,0},{0,3,1,2},{0,3,1,2},{1,5,3,3},{3,4,5,4},{3,5,5,4}};
        return direction>=1&&direction<=4?next[Math.max(0,Math.min(5,index))][direction-1]:index;
    }
    private HomeLayout(){}
}
