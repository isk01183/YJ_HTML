package com.yj.magiccircle

import org.junit.Assert.assertEquals
import org.junit.Test

class VrmMemoryPolicyTest {
    private val mib=1024L*1024
    @Test fun budgetLeavesSystemAndOtherAppsRoom() {
        assertEquals(768*mib,VrmMemoryPolicy.budget(8*1024*mib,4*1024*mib,256*mib,false,false))
        assertEquals(512*mib,VrmMemoryPolicy.budget(2*1024*mib,1536*mib,256*mib,false,false))
        assertEquals(256*mib,VrmMemoryPolicy.budget(4*1024*mib,768*mib,256*mib,false,false))
    }
    @Test fun pressureOrInvalidSnapshotPreventsLoading() {
        assertEquals(0L,VrmMemoryPolicy.budget(4*1024*mib,2*1024*mib,256*mib,true,false))
        assertEquals(0L,VrmMemoryPolicy.budget(4*1024*mib,2*1024*mib,256*mib,false,true))
        assertEquals(0L,VrmMemoryPolicy.budget(4*1024*mib,128*mib,256*mib,false,false))
        assertEquals(0L,VrmMemoryPolicy.budget(0,1,0,false,false))
        assertEquals(0L,VrmMemoryPolicy.budget(1,2,0,false,false))
        assertEquals(0L,VrmMemoryPolicy.budget(10,5,-1,false,false))
    }
}
