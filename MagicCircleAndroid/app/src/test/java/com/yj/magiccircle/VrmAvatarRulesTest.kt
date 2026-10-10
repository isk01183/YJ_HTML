package com.yj.magiccircle

import org.junit.Assert.*
import org.junit.Test

class VrmAvatarRulesTest {
    private fun value()=VrmAvatarDefinition("12345678-1234-4234-8234-123456789abc",0,"별빛",VrmAvatarRules.original())
    private fun rejected(block: ()->Unit){try {block();fail("Invalid avatar accepted")}catch(_: IllegalArgumentException){}}
    @Test fun hairMappingRejectsDonorWholeModel() {
        val v=value();VrmAvatarRules.validate(v)
        assertEquals("c1853aa3c22b5c3b58ba8f819c4b2c4e7bd318aeeefaa9739f7c9f3bb205a708",VrmAvatarRules.modelId("e-hair02"))
        rejected {VrmAvatarRules.validate(v.copy(appearance=v.appearance.copy(hairId="e-hair02")))}
        rejected {VrmAvatarRules.validate(v.copy(appearance=v.appearance.copy(modelId="f4df98833a830f84c6f8bcdb90701e86420bc2fe369e7936fff1cf3b0971573e")))}
        rejected {VrmAvatarRules.modelId("unknown")}
    }
    @Test fun colorsRemainIndependent() {
        val v=value().copy(appearance=value().appearance.copy(dye=VrmDye("#12ABEF","#123456")))
        VrmAvatarRules.validate(v)
        val next=v.appearance.copy(hairId="e-hair02",modelId=VrmAvatarRules.modelId("e-hair02"))
        assertEquals("#12ABEF",next.dye.hair);assertEquals("#123456",next.dye.iris)
        assertEquals("#123456",next.dye.copy(hair=null).iris)
    }
    @Test fun invalidHexAndVersionRejected() {
        assertEquals("#12ABEF",VrmAvatarRules.parseHex("#12abef"))
        assertEquals("#00FF10",VrmAvatarRules.fromRgb(0,255,16))
        listOf("#123","#AABBCCFF","NaN","AABBCC","#GGGGGG").forEach {rejected {VrmAvatarRules.parseHex(it)}}
        rejected {VrmAvatarRules.fromRgb(-1,0,0)};rejected {VrmAvatarRules.fromRgb(0,256,0)}
        val v=value()
        rejected {VrmAvatarRules.validate(v.copy(appearance=v.appearance.copy(profileVersion=2)))}
        rejected {VrmAvatarRules.validate(v.copy(name=" "))};rejected {VrmAvatarRules.validate(v.copy(name="x\ny"))}
        rejected {VrmAvatarRules.validate(v.copy(revision=-1))}
        rejected {VrmAvatarRules.validate(v.copy(id="../../bad"))}
    }
    @Test fun v2RequiresPartAndKeepsIndependentColors() {
        val before=value().appearance.copy(dye=VrmDye("#12ABEF","#123456"))
        val next=before.copy(profileVersion=2,hairId="e-hair02",modelId="a".repeat(64),parts=mapOf("hair" to "b".repeat(64)))
        VrmAvatarRules.validate(next);assertEquals(before.dye,next.dye)
        rejected {VrmAvatarRules.validate(next.copy(parts=emptyMap()))}
        rejected {VrmAvatarRules.validate(next.copy(parts=mapOf("eyes" to "b".repeat(64))))}
        rejected {VrmAvatarRules.validate(next.copy(modelId="../bad"))}
        rejected {VrmAvatarRules.validate(before.copy(parts=next.parts))}
        VrmAvatarRules.validate(before)
    }
}
