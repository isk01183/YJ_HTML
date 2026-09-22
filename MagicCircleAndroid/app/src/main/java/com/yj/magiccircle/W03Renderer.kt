package com.yj.magiccircle

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Picture
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import java.util.Random
import kotlin.math.*

/** Authored W03 geometry from direct-circles.js:160-421. No SVG or image is loaded at runtime. */
class W03Renderer : AutoCloseable {
    private val ink = SanctuaryInk()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gold = 0xfff3dda5.toInt()
    private val blue = 0xff8ee9ff.toInt()
    private var frame = ArtworkGeometry.Frame(0f, 0f, 0f)
    private var width = 0
    private var height = 0
    private var atmosphere: Bitmap? = null
    private val destination = RectF()
    private val leafShape = Path().apply { moveTo(0f, 0f); cubicTo(-4f, -6f, -9f, -17f, -1f, -31f); cubicTo(0f, -23f, 7f, -13f, 0f, 0f); close() }
    private val leafShade = Path().apply { moveTo(0f, 0f); quadTo(-2f, -17f, -1f, -31f); cubicTo(0f, -23f, 7f, -13f, 0f, 0f); close() }
    private val leafVeins = Path().apply { moveTo(0f, 0f); quadTo(-2f, -17f, -1f, -29f); moveTo(-1f, -7f); lineTo(-4f, -13f); moveTo(-1f, -13f); lineTo(-5f, -19f); moveTo(-1f, -19f); lineTo(-4f, -25f); moveTo(-1f, -9f); lineTo(3f, -16f); moveTo(-1f, -15f); lineTo(3f, -21f); moveTo(-1f, -21f); lineTo(1f, -26f) }
    private val boughs = arrayOf(
        floatArrayOf(493f, 861f, 475f, 813f, 439f, 784f, 369f, 786f, 325f, 788f, 276f, 807f, 224f, 829f),
        floatArrayOf(495f, 836f, 468f, 787f, 445f, 748f, 397f, 727f, 358f, 710f, 318f, 724f, 274f, 707f),
        floatArrayOf(490f, 800f, 465f, 770f, 465f, 727f, 439f, 692f, 413f, 658f, 374f, 646f, 354f, 612f),
        floatArrayOf(494f, 767f, 475f, 723f, 486f, 687f, 466f, 650f, 444f, 620f, 416f, 604f, 408f, 578f),
        floatArrayOf(498f, 719f, 489f, 682f, 496f, 644f, 482f, 611f, 469f, 584f, 451f, 567f, 455f, 541f),
        floatArrayOf(494f, 858f, 455f, 805f, 405f, 807f, 361f, 843f, 339f, 862f, 297f, 882f, 257f, 875f),
        floatArrayOf(448f, 785f, 410f, 785f, 390f, 762f, 362f, 750f, 332f, 738f, 299f, 752f, 273f, 745f),
        floatArrayOf(397f, 727f, 371f, 702f, 372f, 674f, 342f, 654f, 324f, 641f, 307f, 640f, 298f, 620f),
        floatArrayOf(439f, 692f, 424f, 690f, 395f, 691f, 377f, 679f, 356f, 666f, 338f, 676f, 320f, 665f),
        floatArrayOf(369f, 786f, 350f, 774f, 346f, 759f, 327f, 761f, 302f, 763f, 287f, 778f, 263f, 777f),
        floatArrayOf(361f, 843f, 343f, 839f, 331f, 823f, 306f, 836f, 292f, 844f, 279f, 844f, 269f, 840f),
        floatArrayOf(466f, 650f, 451f, 645f, 441f, 648f, 428f, 632f, 413f, 615f, 397f, 618f, 386f, 606f),
        floatArrayOf(507f, 862f, 533f, 809f, 570f, 782f, 626f, 789f, 679f, 796f, 721f, 810f, 777f, 831f),
        floatArrayOf(506f, 832f, 538f, 774f, 562f, 742f, 609f, 723f, 646f, 708f, 688f, 723f, 732f, 702f),
        floatArrayOf(511f, 791f, 539f, 754f, 540f, 717f, 565f, 682f, 585f, 652f, 615f, 640f, 638f, 608f),
        floatArrayOf(506f, 757f, 522f, 714f, 519f, 677f, 544f, 643f, 565f, 614f, 584f, 603f, 596f, 576f),
        floatArrayOf(502f, 713f, 514f, 676f, 508f, 639f, 522f, 609f, 536f, 578f, 548f, 567f, 542f, 538f),
        floatArrayOf(507f, 860f, 548f, 807f, 594f, 816f, 634f, 846f, 660f, 866f, 694f, 883f, 738f, 875f),
        floatArrayOf(558f, 785f, 597f, 781f, 615f, 762f, 642f, 750f, 672f, 738f, 701f, 746f, 727f, 739f),
        floatArrayOf(609f, 723f, 637f, 701f, 632f, 677f, 657f, 656f, 678f, 639f, 691f, 641f, 704f, 621f),
        floatArrayOf(565f, 682f, 590f, 691f, 607f, 680f, 624f, 679f, 648f, 677f, 664f, 668f, 678f, 663f),
        floatArrayOf(626f, 789f, 644f, 767f, 660f, 760f, 680f, 765f, 700f, 770f, 717f, 783f, 739f, 777f),
        floatArrayOf(634f, 846f, 657f, 835f, 671f, 829f, 690f, 839f, 707f, 848f, 721f, 844f, 733f, 842f),
        floatArrayOf(544f, 643f, 560f, 645f, 572f, 640f, 583f, 627f, 594f, 615f, 609f, 615f, 620f, 602f)
    )
    private val roots = listOf(
        Path().apply { moveTo(495f, 912f); cubicTo(472f, 965f, 426f, 942f, 382f, 969f); cubicTo(349f, 989f, 311f, 973f, 282f, 1009f) },
        Path().apply { moveTo(492f, 931f); cubicTo(459f, 986f, 416f, 971f, 389f, 1003f); cubicTo(364f, 1034f, 323f, 1026f, 306f, 1052f) },
        Path().apply { moveTo(493f, 945f); cubicTo(466f, 996f, 438f, 1004f, 422f, 1041f); cubicTo(411f, 1066f, 389f, 1091f, 364f, 1108f) },
        Path().apply { moveTo(499f, 955f); cubicTo(492f, 1004f, 459f, 1027f, 458f, 1068f); cubicTo(457f, 1103f, 439f, 1135f, 420f, 1149f) },
        Path().apply { moveTo(490f, 931f); cubicTo(458f, 956f, 411f, 932f, 382f, 948f); cubicTo(355f, 963f, 330f, 950f, 307f, 960f) },
        Path().apply { moveTo(481f, 947f); cubicTo(443f, 963f, 415f, 948f, 387f, 955f); cubicTo(367f, 960f, 354f, 946f, 334f, 948f) },
        Path().apply { moveTo(459f, 967f); cubicTo(419f, 992f, 390f, 978f, 361f, 998f); cubicTo(344f, 1010f, 321f, 1000f, 301f, 1017f) },
        Path().apply { moveTo(441f, 1001f); cubicTo(413f, 1015f, 388f, 1012f, 376f, 1041f); cubicTo(367f, 1060f, 347f, 1063f, 331f, 1074f) },
        Path().apply { moveTo(422f, 1041f); cubicTo(399f, 1051f, 388f, 1041f, 371f, 1052f) },
        Path().apply { moveTo(458f, 1068f); cubicTo(439f, 1082f, 418f, 1072f, 399f, 1089f) },
        Path().apply { moveTo(505f, 912f); cubicTo(532f, 967f, 578f, 945f, 618f, 967f); cubicTo(649f, 984f, 687f, 978f, 718f, 1006f) },
        Path().apply { moveTo(509f, 931f); cubicTo(538f, 984f, 582f, 974f, 611f, 1005f); cubicTo(638f, 1037f, 675f, 1024f, 695f, 1053f) },
        Path().apply { moveTo(507f, 945f); cubicTo(533f, 994f, 565f, 1005f, 580f, 1043f); cubicTo(591f, 1069f, 612f, 1090f, 639f, 1108f) },
        Path().apply { moveTo(501f, 955f); cubicTo(508f, 1002f, 541f, 1029f, 542f, 1069f); cubicTo(543f, 1100f, 560f, 1135f, 580f, 1148f) },
        Path().apply { moveTo(510f, 931f); cubicTo(542f, 955f, 591f, 934f, 620f, 949f); cubicTo(647f, 963f, 673f, 950f, 695f, 961f) },
        Path().apply { moveTo(520f, 947f); cubicTo(559f, 962f, 585f, 947f, 614f, 955f); cubicTo(633f, 961f, 648f, 945f, 667f, 948f) },
        Path().apply { moveTo(541f, 967f); cubicTo(581f, 994f, 611f, 977f, 640f, 998f); cubicTo(657f, 1011f, 680f, 1002f, 700f, 1017f) },
        Path().apply { moveTo(559f, 1001f); cubicTo(587f, 1014f, 612f, 1014f, 624f, 1040f); cubicTo(633f, 1062f, 653f, 1065f, 669f, 1075f) },
        Path().apply { moveTo(580f, 1043f); cubicTo(601f, 1052f, 614f, 1041f, 631f, 1054f) },
        Path().apply { moveTo(542f, 1069f); cubicTo(561f, 1082f, 582f, 1074f, 601f, 1089f) },
        Path().apply { moveTo(500f, 972f); cubicTo(487f, 1023f, 510f, 1053f, 499f, 1102f); cubicTo(493f, 1120f, 499f, 1145f, 500f, 1160f) }
    )
    private val rootlets = listOf(
        Path().apply { moveTo(382f, 969f); quadTo(366f, 973f, 350f, 963f); lineTo(337f, 960f); moveTo(350f, 963f); quadTo(340f, 968f, 329f, 965f) },
        Path().apply { moveTo(359f, 981f); quadTo(349f, 1000f, 334f, 1002f); lineTo(321f, 997f); moveTo(334f, 1002f); lineTo(330f, 1011f) },
        Path().apply { moveTo(389f, 1003f); quadTo(371f, 1004f, 354f, 1014f); quadTo(344f, 1018f, 335f, 1015f); moveTo(354f, 1014f); lineTo(352f, 1025f) },
        Path().apply { moveTo(416f, 983f); quadTo(408f, 995f, 411f, 1007f); moveTo(409f, 998f); lineTo(398f, 1001f) },
        Path().apply { moveTo(391f, 1021f); quadTo(398f, 1036f, 387f, 1053f); moveTo(394f, 1041f); lineTo(406f, 1044f) },
        Path().apply { moveTo(382f, 948f); quadTo(370f, 939f, 355f, 942f); moveTo(369f, 940f); lineTo(365f, 931f) },
        Path().apply { moveTo(422f, 956f); quadTo(402f, 945f, 395f, 932f); moveTo(406f, 949f); lineTo(390f, 948f) },
        Path().apply { moveTo(448f, 945f); quadTo(435f, 931f, 419f, 930f); moveTo(432f, 936f); lineTo(431f, 924f) },
        Path().apply { moveTo(457f, 985f); quadTo(441f, 984f, 427f, 993f); moveTo(442f, 985f); lineTo(434f, 976f) },
        Path().apply { moveTo(444f, 1018f); quadTo(432f, 1032f, 419f, 1030f); moveTo(432f, 1032f); lineTo(427f, 1043f) },
        Path().apply { moveTo(413f, 1060f); quadTo(402f, 1067f, 398f, 1081f); moveTo(404f, 1070f); lineTo(390f, 1071f) },
        Path().apply { moveTo(392f, 1088f); quadTo(376f, 1087f, 367f, 1097f); moveTo(379f, 1089f); lineTo(373f, 1081f) },
        Path().apply { moveTo(460f, 1040f); quadTo(469f, 1054f, 459f, 1068f); moveTo(466f, 1055f); lineTo(477f, 1063f) },
        Path().apply { moveTo(444f, 1107f); quadTo(422f, 1118f, 417f, 1131f); moveTo(430f, 1115f); lineTo(414f, 1117f) },
        Path().apply { moveTo(491f, 992f); quadTo(480f, 1009f, 482f, 1025f); moveTo(482f, 1019f); lineTo(469f, 1026f) },
        Path().apply { moveTo(489f, 1050f); quadTo(476f, 1061f, 481f, 1081f); moveTo(479f, 1071f); lineTo(468f, 1081f) },
        Path().apply { moveTo(473f, 963f); quadTo(463f, 977f, 451f, 977f); moveTo(463f, 974f); lineTo(461f, 984f) },
        Path().apply { moveTo(431f, 974f); quadTo(419f, 972f, 413f, 963f); moveTo(418f, 970f); lineTo(404f, 976f) }
    )
    private val fineRoots = listOf(
        Path().apply { moveTo(488f, 934f); cubicTo(466f, 964f, 439f, 962f, 415f, 978f); cubicTo(396f, 991f, 369f, 995f, 352f, 1019f); cubicTo(342f, 1033f, 324f, 1038f, 317f, 1055f) },
        Path().apply { moveTo(484f, 948f); cubicTo(470f, 981f, 449f, 992f, 433f, 1017f); cubicTo(417f, 1041f, 403f, 1068f, 378f, 1080f); cubicTo(364f, 1087f, 355f, 1100f, 346f, 1103f) },
        Path().apply { moveTo(490f, 959f); cubicTo(475f, 990f, 481f, 1026f, 460f, 1049f); cubicTo(445f, 1063f, 447f, 1091f, 435f, 1104f); cubicTo(424f, 1114f, 426f, 1124f, 417f, 1135f) },
        Path().apply { moveTo(485f, 947f); cubicTo(464f, 959f, 432f, 956f, 411f, 958f); cubicTo(389f, 960f, 368f, 950f, 348f, 959f); cubicTo(334f, 965f, 318f, 958f, 307f, 968f) },
        Path().apply { moveTo(495f, 978f); cubicTo(482f, 1000f, 469f, 1009f, 466f, 1034f); cubicTo(465f, 1055f, 451f, 1069f, 452f, 1084f); cubicTo(454f, 1102f, 443f, 1127f, 436f, 1140f) },
        Path().apply { moveTo(472f, 972f); cubicTo(450f, 977f, 431f, 982f, 415f, 998f); cubicTo(401f, 1016f, 381f, 1011f, 365f, 1020f); cubicTo(354f, 1027f, 339f, 1024f, 330f, 1034f) },
        Path().apply { moveTo(459f, 995f); cubicTo(445f, 1019f, 436f, 1044f, 414f, 1053f); cubicTo(402f, 1058f, 396f, 1079f, 381f, 1090f); cubicTo(370f, 1097f, 372f, 1110f, 361f, 1116f) },
        Path().apply { moveTo(487f, 985f); cubicTo(472f, 1019f, 489f, 1044f, 478f, 1061f); cubicTo(464f, 1083f, 473f, 1108f, 461f, 1127f); cubicTo(454f, 1137f, 457f, 1150f, 452f, 1160f) },
        Path().apply { moveTo(442f, 965f); cubicTo(428f, 951f, 400f, 948f, 387f, 937f); cubicTo(375f, 928f, 357f, 937f, 345f, 931f) },
        Path().apply { moveTo(413f, 981f); cubicTo(395f, 985f, 384f, 971f, 368f, 978f); cubicTo(355f, 984f, 344f, 977f, 332f, 985f) },
        Path().apply { moveTo(445f, 1022f); cubicTo(432f, 1024f, 422f, 1014f, 407f, 1021f); cubicTo(395f, 1026f, 384f, 1022f, 376f, 1031f) },
        Path().apply { moveTo(468f, 1065f); cubicTo(489f, 1091f, 468f, 1105f, 478f, 1123f); cubicTo(485f, 1134f, 475f, 1148f, 480f, 1158f) }
    )
    private val sprigs = arrayOf(
        floatArrayOf(224f, 829f, -62f, 0.85f),
        floatArrayOf(245f, 821f, -27f, 0.65f),
        floatArrayOf(263f, 777f, -61f, 0.75f),
        floatArrayOf(282f, 779f, 23f, 0.68f),
        floatArrayOf(274f, 707f, -52f, 0.85f),
        floatArrayOf(294f, 715f, -16f, 0.78f),
        floatArrayOf(313f, 719f, 25f, 0.77f),
        floatArrayOf(273f, 745f, -58f, 0.62f),
        floatArrayOf(297f, 746f, -8f, 0.66f),
        floatArrayOf(298f, 620f, -26f, 0.64f),
        floatArrayOf(310f, 641f, -60f, 0.72f),
        floatArrayOf(323f, 646f, 16f, 0.64f),
        floatArrayOf(338f, 660f, -40f, 0.64f),
        floatArrayOf(342f, 681f, 36f, 0.69f),
        floatArrayOf(354f, 612f, -12f, 0.8f),
        floatArrayOf(365f, 635f, -42f, 0.7f),
        floatArrayOf(379f, 649f, 31f, 0.75f),
        floatArrayOf(394f, 661f, -25f, 0.74f),
        floatArrayOf(320f, 665f, -54f, 0.65f),
        floatArrayOf(347f, 672f, -4f, 0.64f),
        floatArrayOf(377f, 679f, -35f, 0.58f),
        floatArrayOf(408f, 578f, -18f, 0.76f),
        floatArrayOf(416f, 604f, -43f, 0.71f),
        floatArrayOf(432f, 621f, 27f, 0.77f),
        floatArrayOf(443f, 634f, -24f, 0.57f),
        floatArrayOf(455f, 541f, -5f, 0.82f),
        floatArrayOf(458f, 566f, -45f, 0.6f),
        floatArrayOf(473f, 588f, 27f, 0.58f),
        floatArrayOf(478f, 610f, -31f, 0.6f),
        floatArrayOf(486f, 631f, 29f, 0.66f),
        floatArrayOf(482f, 653f, -25f, 0.72f),
        floatArrayOf(428f, 632f, -54f, 0.58f),
        floatArrayOf(398f, 618f, -16f, 0.63f),
        floatArrayOf(386f, 606f, -35f, 0.72f),
        floatArrayOf(440f, 692f, -22f, 0.77f),
        floatArrayOf(451f, 708f, 38f, 0.8f),
        floatArrayOf(456f, 730f, -36f, 0.74f),
        floatArrayOf(435f, 744f, -66f, 0.65f),
        floatArrayOf(412f, 735f, 7f, 0.68f),
        floatArrayOf(395f, 722f, -28f, 0.7f),
        floatArrayOf(362f, 750f, -27f, 0.66f),
        floatArrayOf(327f, 761f, -18f, 0.58f),
        floatArrayOf(350f, 779f, 33f, 0.63f),
        floatArrayOf(369f, 786f, -23f, 0.7f),
        floatArrayOf(387f, 782f, 20f, 0.77f),
        floatArrayOf(404f, 790f, -28f, 0.65f),
        floatArrayOf(421f, 798f, 24f, 0.72f),
        floatArrayOf(257f, 875f, -73f, 0.8f),
        floatArrayOf(278f, 872f, -29f, 0.6f),
        floatArrayOf(300f, 868f, 12f, 0.63f),
        floatArrayOf(306f, 836f, -39f, 0.62f),
        floatArrayOf(329f, 838f, 22f, 0.72f),
        floatArrayOf(351f, 850f, -12f, 0.63f),
        floatArrayOf(367f, 841f, 25f, 0.67f),
        floatArrayOf(777f, 831f, 66f, 0.82f),
        floatArrayOf(754f, 821f, 24f, 0.72f),
        floatArrayOf(739f, 777f, 63f, 0.76f),
        floatArrayOf(718f, 778f, -21f, 0.7f),
        floatArrayOf(732f, 702f, 50f, 0.81f),
        floatArrayOf(710f, 716f, 16f, 0.7f),
        floatArrayOf(690f, 719f, -25f, 0.69f),
        floatArrayOf(727f, 739f, 53f, 0.68f),
        floatArrayOf(701f, 744f, 9f, 0.67f),
        floatArrayOf(704f, 621f, 25f, 0.7f),
        floatArrayOf(690f, 641f, 55f, 0.7f),
        floatArrayOf(674f, 651f, -14f, 0.7f),
        floatArrayOf(657f, 661f, 39f, 0.66f),
        floatArrayOf(658f, 685f, -28f, 0.71f),
        floatArrayOf(638f, 608f, 12f, 0.8f),
        floatArrayOf(626f, 632f, 42f, 0.74f),
        floatArrayOf(611f, 648f, -30f, 0.78f),
        floatArrayOf(590f, 665f, 29f, 0.76f),
        floatArrayOf(678f, 663f, 53f, 0.6f),
        floatArrayOf(650f, 674f, 4f, 0.7f),
        floatArrayOf(624f, 679f, 31f, 0.62f),
        floatArrayOf(596f, 576f, 18f, 0.75f),
        floatArrayOf(583f, 603f, 43f, 0.72f),
        floatArrayOf(567f, 620f, -26f, 0.8f),
        floatArrayOf(551f, 638f, 26f, 0.64f),
        floatArrayOf(542f, 538f, 5f, 0.83f),
        floatArrayOf(540f, 565f, 43f, 0.64f),
        floatArrayOf(526f, 587f, -26f, 0.61f),
        floatArrayOf(522f, 610f, 33f, 0.65f),
        floatArrayOf(515f, 634f, -29f, 0.65f),
        floatArrayOf(520f, 657f, 29f, 0.7f),
        floatArrayOf(583f, 627f, 48f, 0.63f),
        floatArrayOf(608f, 614f, 17f, 0.65f),
        floatArrayOf(620f, 602f, 35f, 0.73f),
        floatArrayOf(565f, 682f, 22f, 0.79f),
        floatArrayOf(551f, 707f, -35f, 0.78f),
        floatArrayOf(546f, 730f, 37f, 0.71f),
        floatArrayOf(566f, 744f, 64f, 0.68f),
        floatArrayOf(589f, 734f, -8f, 0.7f),
        floatArrayOf(610f, 721f, 28f, 0.72f),
        floatArrayOf(642f, 750f, 30f, 0.7f),
        floatArrayOf(680f, 765f, 18f, 0.65f),
        floatArrayOf(652f, 780f, -32f, 0.6f),
        floatArrayOf(626f, 789f, 24f, 0.7f),
        floatArrayOf(611f, 788f, -20f, 0.76f),
        floatArrayOf(595f, 791f, 29f, 0.64f),
        floatArrayOf(578f, 801f, -24f, 0.7f),
        floatArrayOf(738f, 875f, 69f, 0.81f),
        floatArrayOf(718f, 873f, 28f, 0.62f),
        floatArrayOf(697f, 870f, -12f, 0.63f),
        floatArrayOf(690f, 839f, 39f, 0.7f),
        floatArrayOf(670f, 840f, -23f, 0.72f),
        floatArrayOf(650f, 852f, 14f, 0.61f),
        floatArrayOf(634f, 846f, -25f, 0.69f),
        floatArrayOf(502f, 574f, 0f, 0.86f),
        floatArrayOf(500f, 598f, -29f, 0.6f),
        floatArrayOf(501f, 620f, 29f, 0.67f),
        floatArrayOf(499f, 641f, -32f, 0.72f),
        floatArrayOf(502f, 665f, 31f, 0.7f),
        floatArrayOf(501f, 690f, -29f, 0.6f)
    )
    private val borderLeaves = arrayOf(
        floatArrayOf(56f, 98f, -40f),
        floatArrayOf(48f, 125f, 25f),
        floatArrayOf(52f, 160f, -37f),
        floatArrayOf(60f, 190f, 42f),
        floatArrayOf(69f, 226f, -30f),
        floatArrayOf(65f, 260f, 48f),
        floatArrayOf(59f, 292f, -42f),
        floatArrayOf(57f, 327f, 28f),
        floatArrayOf(89f, 1451f, -52f),
        floatArrayOf(112f, 1484f, 30f),
        floatArrayOf(143f, 1513f, -38f),
        floatArrayOf(177f, 1538f, 41f),
        floatArrayOf(215f, 1560f, -24f),
        floatArrayOf(251f, 1590f, 39f),
        floatArrayOf(286f, 1629f, -20f),
        floatArrayOf(314f, 1659f, 38f),
        floatArrayOf(81f, 1150f, -46f),
        floatArrayOf(75f, 1185f, 21f),
        floatArrayOf(78f, 1231f, -35f),
        floatArrayOf(87f, 1270f, 36f),
        floatArrayOf(57f, 641f, -36f),
        floatArrayOf(68f, 602f, 33f),
        floatArrayOf(93f, 561f, -23f)
    )
    private val frameAnchors = arrayOf(
        floatArrayOf(32f, 87f, 44f, 1.1f),
        floatArrayOf(67f, 36f, -28f, 1.15f),
        floatArrayOf(104f, 94f, 53f, 1.05f),
        floatArrayOf(21f, 190f, -20f, 1.3f),
        floatArrayOf(60f, 241f, -42f, 1.15f),
        floatArrayOf(115f, 179f, 23f, 0.92f),
        floatArrayOf(42f, 303f, -25f, 1.05f),
        floatArrayOf(11f, 363f, -55f, 1.2f),
        floatArrayOf(166f, 39f, 44f, 0.8f),
        floatArrayOf(8f, 23f, 24f, 1.4f),
        floatArrayOf(175f, 175f, -18f, 0.6f),
        floatArrayOf(202f, 221f, 24f, 0.57f),
        floatArrayOf(4f, 1394f, -30f, 1.1f),
        floatArrayOf(46f, 1420f, -55f, 1.3f),
        floatArrayOf(24f, 1494f, 22f, 1.55f),
        floatArrayOf(91f, 1454f, -22f, 1.2f),
        floatArrayOf(143f, 1504f, -48f, 1.25f),
        floatArrayOf(44f, 1590f, 16f, 1.7f),
        floatArrayOf(112f, 1581f, -44f, 1.34f),
        floatArrayOf(167f, 1625f, 17f, 1.2f),
        floatArrayOf(226f, 1645f, -35f, 1.15f),
        floatArrayOf(75f, 1683f, -31f, 1.55f),
        floatArrayOf(8f, 1716f, 20f, 1.7f),
        floatArrayOf(195f, 1742f, -27f, 1.35f),
        floatArrayOf(269f, 1714f, 18f, 0.92f),
        floatArrayOf(20f, 1314f, -48f, 1.05f),
        floatArrayOf(65f, 1360f, -32f, 1.05f)
    )
    private val veils = listOf(
        Path().apply { moveTo(24f, 12f); cubicTo(48f, 125f, 225f, 164f, 285f, 312f); cubicTo(320f, 415f, 407f, 429f, 423f, 506f) },
        Path().apply { moveTo(121f, 15f); cubicTo(76f, 133f, 174f, 225f, 242f, 294f); cubicTo(292f, 344f, 310f, 439f, 358f, 507f) },
        Path().apply { moveTo(976f, 12f); cubicTo(952f, 125f, 775f, 164f, 715f, 312f); cubicTo(680f, 415f, 593f, 429f, 577f, 506f) },
        Path().apply { moveTo(879f, 15f); cubicTo(924f, 133f, 826f, 225f, 758f, 294f); cubicTo(708f, 344f, 690f, 439f, 642f, 507f) },
        Path().apply { moveTo(-14f, 1300f); cubicTo(142f, 1304f, 178f, 1435f, 222f, 1559f); cubicTo(265f, 1670f, 362f, 1732f, 391f, 1786f) },
        Path().apply { moveTo(13f, 1390f); cubicTo(116f, 1424f, 105f, 1505f, 191f, 1603f); cubicTo(241f, 1659f, 268f, 1734f, 295f, 1788f) },
        Path().apply { moveTo(1014f, 1300f); cubicTo(858f, 1304f, 822f, 1435f, 778f, 1559f); cubicTo(735f, 1670f, 638f, 1732f, 609f, 1786f) },
        Path().apply { moveTo(987f, 1390f); cubicTo(884f, 1424f, 895f, 1505f, 809f, 1603f); cubicTo(759f, 1659f, 732f, 1734f, 705f, 1788f) }
    )
    private val attachedLeaves = ArrayList<FloatArray>()
    private val limbs = boughs.mapIndexed { i, v -> taperedLimb(v, i) }
    private val beforeLight = record { constellation(it); rings(it) }
    private val ringLight = record { c ->
        // SVG dash pattern 46 600 on the radius-433 circle, starting at the rightmost point.
        val circumference = 2 * PI * 433
        var distance = 0.0
        while (distance < circumference) {
            val arc = Path().apply { addArc(67f, 398f, 933f, 1264f,
                (distance / circumference * 360).toFloat(), (min(46.0, circumference-distance) / circumference * 360).toFloat()) }
            stroke(c, arc, blue, 2f, .65f)
            distance += 646
        }
    }
    private val afterLight = record { c ->
        runes(c); orbits(c); crown(c); garden(c)
        bloom(c, 500f, ArtworkGeometry.W03_CORE_Y, 265f,
            intArrayOf(0xa6fff9dc.toInt(), 0x80fff5ba.toInt(), 0x2e6ee2fa, 0x0036b9ff), floatArrayOf(0f,.08f,.35f,1f))
        tree(c); leaves(c); jewels(c); globe(c); glints(c); fixedStars(c); medallion(c)
    }
    private val edgeFoliage = record { c -> frameAnchors.forEachIndexed { i, a ->
        val shade = intArrayOf(0xff659eab.toInt(),0xff9fc6b4.toInt(),0xff1e4b61.toInt(),0xffb3dcce.toInt(),0xff387287.toInt())[i%5]
        spray(c,a[0],a[1],a[2],a[3],shade); spray(c,1000-a[0],a[1],-a[2],a[3]*.96f,shade)
    } }
    private val foreground = record { c ->
        listOf(-152f,-128f,-111f,-69f,-50f,-29f,24f,48f,71f,108f,132f,156f).forEachIndexed { i,a ->
            val (x,y)=polar(444f,a); spray(c,x,y,a,.52f,if(i%3==0)0xfff5e3b4.toInt() else 0xffb8e7d5.toInt())
        }
        arrayOf(floatArrayOf(166f,414f,.8f,-14f),floatArrayOf(834f,414f,.8f,14f),floatArrayOf(113f,1191f,.7f,28f),
            floatArrayOf(887f,1191f,.7f,-28f),floatArrayOf(122f,338f,.66f,12f),floatArrayOf(878f,338f,.66f,-12f))
            .forEach { butterfly(c,it[0],it[1],it[2],it[3]) }
    }

    fun prepare(width: Int, height: Int) {
        if (this.width == width && this.height == height) return
        this.width=width; this.height=height
        frame=ArtworkGeometry.fit(width,height,ArtworkGeometry.W03_WIDTH,ArtworkGeometry.W03_HEIGHT)
        atmosphere?.recycle(); atmosphere=null
        if (frame.scale <= 0f) return
        // One viewport cache, capped to 1024 on its long side; Paths/Pictures keep core detail sharp.
        val ratio=minOf(1f,1024f/maxOf(width,height))
        val bitmap=Bitmap.createBitmap(maxOf(1,(width*ratio).roundToInt()),maxOf(1,(height*ratio).roundToInt()),Bitmap.Config.ARGB_8888)
        val c=Canvas(bitmap); c.scale(bitmap.width/1000f,bitmap.height/1778f)
        drawAtmosphere(c)
        atmosphere=bitmap
        destination.set(0f,0f,width.toFloat(),height.toFloat())
    }

    fun draw(canvas: Canvas, elapsedMs: Long, animated: Boolean) {
        if (frame.scale <= 0f) return
        canvas.drawColor(0xff020a12.toInt())
        atmosphere?.let { paint.reset(); paint.isFilterBitmap=true; canvas.drawBitmap(it,null,destination,paint) }
        val save=canvas.save()
        canvas.translate(frame.left,frame.top); canvas.scale(frame.scale,frame.scale)
        canvas.drawPicture(beforeLight)
        val ring=canvas.save()
        canvas.rotate(ArtworkGeometry.w03RingAngle(elapsedMs,animated),ArtworkGeometry.W03_AXIS_X,ArtworkGeometry.W03_RING_Y)
        canvas.drawPicture(ringLight); canvas.restoreToCount(ring)
        canvas.drawPicture(afterLight); canvas.restoreToCount(save)
        // Corner framing follows the viewport, never the uniformly fitted tree/rings.
        val edge=canvas.save(); canvas.scale(width/1000f,height/1778f); canvas.drawPicture(edgeFoliage); canvas.restoreToCount(edge)
        val front=canvas.save(); canvas.translate(frame.left,frame.top); canvas.scale(frame.scale,frame.scale)
        canvas.drawPicture(foreground); canvas.restoreToCount(front)
    }

    override fun close() {
        atmosphere?.recycle(); atmosphere=null; width=0; height=0
        frame=ArtworkGeometry.Frame(0f,0f,0f)
    }

    private fun record(draw: (Canvas)->Unit)=Picture().apply { draw(beginRecording(1000,1778)); endRecording() }
    private fun circle(x:Float,y:Float,r:Float)=Path().apply { addCircle(x,y,r,Path.Direction.CW) }
    private fun polar(r:Float,a:Float,x:Float=500f,y:Float=831f):Pair<Float,Float> {
        val rad=Math.toRadians(a.toDouble()); return round3(x+r*cos(rad).toFloat()) to round3(y+r*sin(rad).toFloat())
    }
    private fun round3(v:Float)=round(v*1000)/1000
    private fun stroke(c:Canvas,p:Path,color:Int=gold,width:Float=1f,glow:Float=0f,alpha:Int=255) {
        if(glow>0f) {
            ink.stroke(c,p,color,width+10f,(alpha*glow*.047f).toInt())
            ink.stroke(c,p,color,width+5f,(alpha*glow*.11f).toInt())
            ink.stroke(c,p,color,width+2f,(alpha*glow*.165f).toInt())
        }
        ink.stroke(c,p,color,width,alpha)
    }
    private fun fill(c:Canvas,p:Path,color:Int,alpha:Int=255) { ink.fill(c,p,color,alpha) }
    private fun diamond(x:Float,y:Float,r:Float)=Path().apply { moveTo(x,y-r); lineTo(x+r,y); lineTo(x,y+r); lineTo(x-r,y); close() }
    private fun at(c:Canvas,x:Float,y:Float,a:Float=0f,sx:Float=1f,sy:Float=sx,body:()->Unit) {
        val save=c.save(); c.translate(x,y); c.rotate(a); c.scale(sx,sy); body(); c.restoreToCount(save)
    }
    private fun ticks(x:Float,y:Float,r:Float,count:Int,length:Float)=Path().apply {
        repeat(count) { i -> val a=i*360f/count; val start=polar(r,a,x,y); val end=polar(r+length*(if(i%5==0)1.7f else 1f),a,x,y)
            moveTo(start.first,start.second); lineTo(end.first,end.second) }
    }
    private fun bloom(c:Canvas,x:Float,y:Float,r:Float,colors:IntArray,stops:FloatArray) {
        paint.reset(); paint.isAntiAlias=true; paint.shader=RadialGradient(x,y,r,colors,stops,Shader.TileMode.CLAMP)
        c.drawCircle(x,y,r,paint); paint.shader=null
    }
    private fun cyan(c:Canvas,x:Float,y:Float,r:Float)=bloom(c,x,y,r,
        intArrayOf(0xd9eeffff.toInt(),0xccb2f4ff.toInt(),0x8c22bbff.toInt(),0x291685e7,0x001664c1),floatArrayOf(0f,.06f,.24f,.55f,1f))
    private fun wood(c:Canvas,p:Path) {
        paint.reset(); paint.isAntiAlias=true
        val bounds=RectF();p.computeBounds(bounds,true)
        paint.shader=LinearGradient(bounds.left,bounds.top,bounds.right,bounds.top,
            intArrayOf(0xff77d8ef.toInt(),0xffffeec4.toInt(),0xfffffdef.toInt(),0xffffdfa2.toInt(),0xff81d8ef.toInt()),floatArrayOf(0f,.35f,.52f,.72f,1f),Shader.TileMode.CLAMP)
        c.drawPath(p,paint); paint.shader=null
    }
    private fun leaf(c:Canvas,x:Float,y:Float,a:Float,size:Float=1f,color:Int=0xffb9f5ee.toInt(),spread:Float=1f,glow:Float=0f) {
        at(c,x,y,a,round3(size*spread),size) {
            fill(c,leafShape,color,209); stroke(c,leafShape,color,.55f,glow)
            fill(c,leafShade,0xff153d53.toInt(),43); stroke(c,leafVeins,0xfff3ffff.toInt(),.38f,0f,204)
        }
    }
    private fun spray(c:Canvas,x:Float,y:Float,a:Float,s:Float,shade:Int) = at(c,x,y,a,s) {
        stroke(c,Path().apply { moveTo(0f, 45f); cubicTo(7f, 15f, -7f, -24f, 1f, -76f) },0xff8ea99d.toInt(),.65f)
        arrayOf(1f to -67f,-2f to -47f,-2f to -26f,1f to -4f,3f to 18f).forEachIndexed { i,p ->
            leaf(c,p.first,p.second,-43f-i*6,.56f+i*.045f,shade,1.55f)
            leaf(c,p.first,p.second+9,39f+i*8,.49f+i*.038f,shade,1.45f)
        }
        leaf(c,1f,-73f,4f,.64f,shade,1.3f)
    }
    private fun butterfly(c:Canvas,x:Float,y:Float,s:Float,a:Float=0f)=at(c,x,y,a,s) {
        val wing=Path().apply { moveTo(0f, 0f); cubicTo(-4f, -18f, -17f, -28f, -31f, -28f); cubicTo(-29f, -12f, -18f, -3f, 0f, 0f); close(); moveTo(-1f, 2f); cubicTo(-13f, -2f, -26f, 2f, -20f, 14f); cubicTo(-14f, 24f, -5f, 15f, -1f, 2f); close() }
        val veins=Path().apply { moveTo(-1f, 0f); lineTo(-27f, -25f); moveTo(-2f, 2f); lineTo(-17f, 15f); moveTo(-3f, 0f); quadTo(-19f, -14f, -27f, -17f) }
        for(sign in listOf(1f,-1f)) at(c,0f,0f,0f,sign,1f) {
            fill(c,wing,0xffb7efff.toInt(),186); stroke(c,wing,0xffe9ffff.toInt(),.7f,.6f); stroke(c,veins,0xffedffff.toInt(),.8f)
        }
        stroke(c,Path().apply { moveTo(0f, 10f); quadTo(-2f, -4f, 0f, -9f); moveTo(0f, -7f); quadTo(-6f, -17f, -9f, -16f); moveTo(0f, -7f); quadTo(6f, -17f, 9f, -16f) },0xfffff9df.toInt(),1.1f)
    }
    private fun ray(c:Canvas,x:Float,y:Float,r:Float,color:Int) {
        val p=Path().apply { moveTo(x,y-r); lineTo(x+1.2f,y-2); lineTo(x+r*.7f,y); lineTo(x+1.2f,y+2)
            lineTo(x,y+r); lineTo(x-1.2f,y+2); lineTo(x-r*.7f,y); lineTo(x-1.2f,y-2); close() }
        fill(c,p,color); stroke(c,p,0xfffff7dc.toInt(),.25f,1f)
    }
    private fun star(c:Canvas,x:Float,y:Float,r:Float) {
        val p=Path().apply { moveTo(x,y-r); quadTo(x+1.3f,y-1.3f,x+r*.62f,y); quadTo(x+1.3f,y+1.3f,x,y+r)
            quadTo(x-1.3f,y+1.3f,x-r*.62f,y); quadTo(x-1.3f,y-1.3f,x,y-r); close() }
        fill(c,p,0xfffff3d1.toInt()); stroke(c,p,0xffb3e6d3.toInt(),.6f,.25f)
    }

    // Exactly 24 samples per authored cubic. The same samples anchor the extra leaves.
    private fun taperedLimb(v:FloatArray,index:Int):Path {
        val left=ArrayList<Pair<Float,Float>>(); val right=ArrayList<Pair<Float,Float>>()
        val segments=(v.size-2)/6; val width=if(index<6 || index in 12..17)7f else 3f
        repeat(segments) { segment ->
            val k=segment*6
            for(j in (if(segment==0)0 else 1)..24) {
                val t=j/24.0; val u=1-t; val progress=(segment+t)/segments
                val x=u*u*u*v[k]+3*u*u*t*v[k+2]+3*u*t*t*v[k+4]+t*t*t*v[k+6]
                val y=u*u*u*v[k+1]+3*u*u*t*v[k+3]+3*u*t*t*v[k+5]+t*t*t*v[k+7]
                val dx=3*u*u*(v[k+2]-v[k])+6*u*t*(v[k+4]-v[k+2])+3*t*t*(v[k+6]-v[k+4])
                val dy=3*u*u*(v[k+3]-v[k+1])+6*u*t*(v[k+5]-v[k+3])+3*t*t*(v[k+7]-v[k+5])
                val norm=hypot(dx,dy).let { if(it==0.0)1.0 else it }; val half=(.25+width*(1-progress).pow(1.35))/2
                left.add(round3((x-dy/norm*half).toFloat()) to round3((y+dx/norm*half).toFloat()))
                right.add(round3((x+dy/norm*half).toFloat()) to round3((y-dx/norm*half).toFloat()))
                if(width==7f && j in (if(segment==0)listOf(8,14,20) else listOf(10,18)) && abs(x-500)>23) {
                    val a=atan2(dy,dx)*180/PI+90
                    attachedLeaves.add(floatArrayOf(round3(x.toFloat()),round3(y.toFloat()),round3((a-43).toFloat()),round3((a+48).toFloat()),index.toFloat()))
                }
            }
        }
        return Path().apply { (left+right.reversed()).forEachIndexed { i,p -> if(i==0)moveTo(p.first,p.second) else lineTo(p.first,p.second) }; close() }
    }
    private fun tree(c:Canvas) {
        val trunk=Path().apply { moveTo(480f, 913f); cubicTo(496f, 883f, 493f, 856f, 486f, 828f); cubicTo(480f, 806f, 464f, 790f, 445f, 779f); cubicTo(472f, 785f, 485f, 795f, 494f, 812f); cubicTo(493f, 781f, 488f, 756f, 482f, 735f); cubicTo(495f, 748f, 498f, 759f, 501f, 775f); cubicTo(507f, 749f, 516f, 729f, 528f, 715f); cubicTo(515f, 749f, 509f, 776f, 510f, 813f); cubicTo(522f, 792f, 539f, 785f, 560f, 783f); cubicTo(537f, 796f, 519f, 817f, 515f, 844f); cubicTo(511f, 872f, 513f, 897f, 525f, 920f); cubicTo(541f, 944f, 565f, 961f, 606f, 970f); cubicTo(559f, 966f, 530f, 952f, 508f, 931f); cubicTo(510f, 969f, 522f, 991f, 549f, 1010f); cubicTo(520f, 1000f, 506f, 984f, 500f, 969f); cubicTo(492f, 991f, 476f, 1004f, 452f, 1013f); cubicTo(478f, 993f, 488f, 973f, 490f, 933f); cubicTo(471f, 952f, 443f, 964f, 399f, 972f); cubicTo(436f, 958f, 467f, 940f, 480f, 913f); close() }
        wood(c,trunk); stroke(c,trunk,0xfffff3cc.toInt(),1.4f,1f)
        fill(c,Path().apply { moveTo(496f, 855f); cubicTo(495f, 767f, 493f, 680f, 500f, 548f); cubicTo(508f, 679f, 505f, 766f, 505f, 855f); close() },0xfffff4d9.toInt())
        limbs.forEach { fill(c,it,0xfffff1d2.toInt()) }
        roots.forEachIndexed { i,p -> stroke(c,p,0xfffff4d6.toInt(),if(i%5==0)2.8f else 1.9f,1f) }
        for(sign in listOf(1f,-1f)) at(c,if(sign<0)1000f else 0f,0f,0f,sign,1f) {
            rootlets.forEach { stroke(c,it,0xfffff4d6.toInt(),.75f,1f) }
            fineRoots.forEachIndexed { i,p -> stroke(c,p,0xfffff4d6.toInt(),if(i<8)1.15f else .65f,1f) }
        }
        arrayOf(floatArrayOf(382f,969f,-1f),floatArrayOf(389f,1003f,-1f),floatArrayOf(422f,1041f,-1f),floatArrayOf(458f,1068f,-1f),floatArrayOf(334f,948f,-1f),floatArrayOf(361f,998f,-1f),
            floatArrayOf(618f,967f,1f),floatArrayOf(611f,1005f,1f),floatArrayOf(580f,1043f,1f),floatArrayOf(542f,1069f,1f),floatArrayOf(667f,948f,1f),floatArrayOf(640f,998f,1f)).forEach { a ->
            val x=a[0];val y=a[1];val side=a[2]
            repeat(4) { j -> val ex=x+side*(38+j*8); val ey=y+13+j*10
                stroke(c,Path().apply { moveTo(x,y); cubicTo(x+side*(14+j*3),y+10,x+side*(22+j*8),y+10+j*8,ex,ey) },0xfffff4d6.toInt(),1f,1f)
                stroke(c,Path().apply { moveTo(ex,ey); rQuadTo(side*9,if(j%2==1)9f else -7f,side*21,if(j%2==1)12f else -4f)
                    rMoveTo(-side*11,if(j%2==1)-6f else 1f); rQuadTo(side*5,9f,side*16,10f) },0xfffff4d6.toInt(),.6f,1f,230)
            }
        }
        sprigs.forEachIndexed { i,a -> stroke(c,Path().apply { moveTo(a[0],a[1]); rQuadTo(round3(sin(Math.toRadians(a[2].toDouble())).toFloat()*5),10f,if(i%2==1)3f else -3f,18f) },0xfffff4d6.toInt(),.7f,1f,204) }
    }
    private fun leaves(c:Canvas) {
        sprigs.forEachIndexed { i,a -> at(c,a[0],a[1],a[2]) {
            val shade=if(i%5==0)0xfffff0c8.toInt() else if(i%3==0)0xffe9fff1.toInt() else 0xffa3eeff.toInt()
            val s=a[3]
            leaf(c,0f,-14f,-7f,s*.96f,shade,glow=.68f); leaf(c,0f,-4f,-49f,s*.77f,shade,glow=.68f)
            leaf(c,0f,5f,43f,s*.8f,shade,glow=.68f); leaf(c,0f,14f,-65f,s*.76f,shade,glow=.68f); leaf(c,0f,22f,60f,s*.69f,shade,glow=.68f)
            stroke(c,Path().apply { moveTo(0f, 29f); quadTo(-2f, 2f, 0f, -18f) },0xfffff4ce.toInt(),.8f,.68f)
        } }
        attachedLeaves.forEach { a ->
            leaf(c,a[0],a[1],a[2],.57f,if(a[4].toInt()%3!=0)0xffc4f5ff.toInt() else 0xfffff3d7.toInt(),glow=.68f)
            leaf(c,a[0],a[1],a[3],.49f,if(a[4].toInt()%3!=0)0xffe8ffff.toInt() else 0xffe7d3a7.toInt(),glow=.68f)
        }
    }

    private fun constellation(c:Canvas) {
        stroke(c,circle(500f,831f,244f),0xff9be9f3.toInt(),.7f,.5f); stroke(c,circle(500f,831f,213f),0xff9be9f3.toInt(),.5f,.5f)
        stroke(c,Path().apply { moveTo(500f, 570f); lineTo(713f, 709f); lineTo(744f, 875f); lineTo(631f, 1066f); lineTo(369f, 1066f); lineTo(256f, 875f); lineTo(287f, 709f); close(); moveTo(500f, 570f); lineTo(631f, 1066f); lineTo(287f, 709f); lineTo(744f, 875f); lineTo(369f, 1066f); lineTo(713f, 709f); lineTo(256f, 875f); close() },0xff9be9f3.toInt(),.6f,.5f,153)
        arrayOf(500f to 570f,713f to 709f,744f to 875f,631f to 1066f,369f to 1066f,256f to 875f,287f to 709f).forEach { p -> fill(c,circle(p.first,p.second,3f),0xffc2faff.toInt()) }
    }
    private fun rings(c:Canvas) {
        ArtworkGeometry.w03Radii.forEachIndexed { i,r -> stroke(c,circle(500f,831f,r),if(i==1||i==3)blue else gold,if(i==1)2.8f else if(i==3)1.8f else 1f,.62f,if(i==7)115 else 230) }
        repeat(104) { i -> val a=i*3.46f-151;val r=439+sin(i*2.7).toFloat()*1.6f;val span=.3f+(i%5)*.45f
            stroke(c,Path().apply { addArc(500-r,831-r,500+r,831+r,a,span) },if(i%5!=0)0xffb1f6ff.toInt() else 0xfffff9d3.toInt(),if(i%7==0)3f else 1.2f,.62f,(255*(.4f+i%3*.2f)).toInt()) }
        // Authored SVG circular arcs (radius 440, clockwise); centers differ slightly from the ring.
        stroke(c,Path().apply { addArc(59.48651f,390.50807f,939.48651f,1270.50807f,-139.88405f,96.45007f) },0xff66ddff.toInt(),8f,1f,35)
        stroke(c,Path().apply { addArc(56.71855f,387.9735f,936.71855f,1267.9735f,13.80962f,117.19945f) },0xff66ddff.toInt(),8f,1f,35)
        stroke(c,Path().apply { addArc(60.41007f,389.08656f,940.41007f,1269.08656f,170.85728f,24.44034f) },0xff66ddff.toInt(),8f,1f,35)
    }
    private fun runes(c:Canvas) {
        stroke(c,ticks(500f,831f,356f,180,5f),gold,.8f,.28f)
        stroke(c,ticks(500f,831f,444f,120,3f),gold,.8f,.28f)
        val glyphs=listOf(Path().apply { moveTo(-5f, 3f); lineTo(-2f, -3f); lineTo(0f, 2f); lineTo(4f, -1f); moveTo(-2f, -3f); lineTo(-1f, -7f); moveTo(0f, 2f); lineTo(1f, 5f) },
Path().apply { moveTo(-5f, 0f); quadTo(-2f, 5f, 0f, -1f); lineTo(2f, -5f); lineTo(4f, 1f); moveTo(-1f, 1f); lineTo(5f, 3f) },
Path().apply { moveTo(-5f, 3f); lineTo(-2f, 0f); lineTo(-2f, -5f); moveTo(-2f, 0f); lineTo(1f, 2f); lineTo(4f, -3f); moveTo(2f, 1f); lineTo(2f, 6f) },
Path().apply { moveTo(-5f, 2f); lineTo(-2f, -2f); lineTo(0f, 2f); lineTo(2f, -2f); lineTo(5f, 0f); moveTo(0f, 2f); lineTo(0f, 5f) },
Path().apply { moveTo(-4f, -3f); quadTo(0f, -6f, 2f, -2f); lineTo(-1f, 2f); lineTo(4f, 3f); moveTo(-2f, 2f); lineTo(-2f, 6f) },
Path().apply { moveTo(-5f, 1f); lineTo(-1f, 1f); lineTo(1f, -5f); lineTo(1f, 4f); lineTo(5f, -1f); moveTo(-1f, 1f); lineTo(-3f, -3f) },
Path().apply { moveTo(-4f, 4f); lineTo(-1f, -4f); lineTo(1f, 1f); lineTo(5f, -2f); moveTo(1f, 1f); lineTo(2f, 5f) },
Path().apply { moveTo(-5f, -1f); lineTo(-2f, 2f); lineTo(1f, -3f); lineTo(4f, 1f); moveTo(1f, -3f); lineTo(1f, -6f); moveTo(-2f, 2f); lineTo(-2f, 5f) },
Path().apply { moveTo(-5f, 2f); quadTo(-1f, -5f, 1f, 0f); lineTo(4f, -2f); moveTo(-3f, 2f); lineTo(2f, 4f); moveTo(1f, 0f); lineTo(3f, -6f) },
Path().apply { moveTo(-5f, 0f); lineTo(-2f, -4f); lineTo(0f, 3f); lineTo(4f, -1f); moveTo(-2f, -4f); lineTo(-3f, -7f); moveTo(0f, 3f); lineTo(4f, 5f) },
Path().apply { moveTo(-5f, 1f); lineTo(-3f, -3f); quadTo(1f, 3f, 2f, -3f); lineTo(4f, 1f); moveTo(0f, 1f); lineTo(-1f, 5f) },
Path().apply { moveTo(-5f, 3f); lineTo(-2f, 2f); lineTo(-1f, -5f); lineTo(2f, -2f); lineTo(4f, -4f); moveTo(-1f, 1f); lineTo(3f, 4f) },
Path().apply { moveTo(-5f, 0f); quadTo(-2f, -4f, 0f, 1f); lineTo(3f, -1f); lineTo(5f, 2f); moveTo(-1f, -1f); lineTo(-2f, -6f); moveTo(3f, -1f); lineTo(3f, -5f) },
Path().apply { moveTo(-5f, 2f); lineTo(-3f, -1f); lineTo(0f, 3f); lineTo(3f, -5f); lineTo(4f, 1f); moveTo(0f, 3f); lineTo(-1f, 6f) },
Path().apply { moveTo(-5f, -1f); lineTo(-3f, 3f); lineTo(1f, -3f); lineTo(4f, 0f); moveTo(1f, -3f); lineTo(0f, -7f); moveTo(-2f, 2f); lineTo(3f, 5f) },
Path().apply { moveTo(-5f, 2f); quadTo(-1f, 4f, -1f, -2f); lineTo(0f, -5f); lineTo(3f, -1f); lineTo(5f, -2f); moveTo(1f, 1f); lineTo(3f, 4f) },
Path().apply { moveTo(-5f, 3f); lineTo(-3f, -3f); lineTo(0f, -1f); lineTo(2f, -5f); moveTo(0f, -1f); lineTo(0f, 4f); lineTo(5f, 2f) },
Path().apply { moveTo(-5f, -1f); lineTo(-2f, 2f); lineTo(0f, -4f); lineTo(2f, 2f); lineTo(5f, 0f); moveTo(0f, -4f); lineTo(-1f, -7f); moveTo(-2f, 2f); lineTo(-3f, 5f) },
Path().apply { moveTo(-5f, 0f); lineTo(-2f, -3f); lineTo(0f, 1f); quadTo(3f, 4f, 5f, -1f); moveTo(0f, 1f); lineTo(0f, 5f); moveTo(2f, 1f); lineTo(3f, -5f) },
Path().apply { moveTo(-5f, 2f); lineTo(-2f, 0f); lineTo(0f, -5f); lineTo(3f, -1f); lineTo(4f, 4f); moveTo(-2f, 0f); lineTo(0f, 4f); moveTo(3f, -1f); lineTo(5f, -4f) })
        ArtworkGeometry.w03Runes().forEach { r -> at(c,r.x,r.y,r.rotation,.9f) { stroke(c,glyphs[r.glyph],gold,.8f,.28f) } }
    }
    private fun orbits(c:Canvas) {
        stroke(c,Path().apply { moveTo(106f, 590f); cubicTo(286f, 548f, 836f, 881f, 801f, 925f); cubicTo(766f, 969f, 326f, 743f, 123f, 635f); cubicTo(73f, 608f, 66f, 593f, 106f, 590f); close() },0xfffaeccb.toInt(),1.7f,.66f)
        stroke(c,Path().apply { moveTo(900f, 590f); cubicTo(716f, 553f, 173f, 882f, 202f, 926f); cubicTo(235f, 975f, 675f, 742f, 877f, 635f); cubicTo(927f, 609f, 937f, 593f, 900f, 590f); close() },0xfffaeccb.toInt(),1.7f,.66f)
        stroke(c,Path().apply { moveTo(78f, 984f); cubicTo(1f, 1076f, 119f, 1101f, 272f, 1081f); cubicTo(501f, 1050f, 735f, 938f, 878f, 815f); moveTo(920f, 984f); cubicTo(1001f, 1078f, 881f, 1101f, 728f, 1081f); cubicTo(499f, 1050f, 264f, 938f, 122f, 815f) },0xfffaeccb.toInt(),1.7f,.66f,217)
    }
    private fun crown(c:Canvas) {
        stroke(c,circle(500f,170f,69f),gold,1.15f,.5f);stroke(c,circle(500f,170f,58f),gold,.6f,.5f)
        stroke(c,ticks(500f,170f,63f,48,3f),gold,1.15f,.5f)
        listOf(Path().apply { moveTo(421f, 128f); cubicTo(388f, 154f, 388f, 195f, 421f, 217f); cubicTo(395f, 189f, 401f, 150f, 421f, 128f); close() },Path().apply { moveTo(579f, 128f); cubicTo(612f, 154f, 612f, 195f, 579f, 217f); cubicTo(605f, 189f, 599f, 150f, 579f, 128f); close() }).forEach { wood(c,it);stroke(c,it,gold,1.15f,.5f) }
        stroke(c,Path().apply { moveTo(500f, 12f); lineTo(500f, 123f); moveTo(500f, 217f); lineTo(500f, 296f); moveTo(430f, 170f); lineTo(570f, 170f) },gold,.8f,.5f)
        stroke(c,Path().apply { moveTo(439f, 282f); quadTo(414f, 251f, 368f, 242f); quadTo(348f, 234f, 335f, 215f); moveTo(560f, 282f); quadTo(584f, 252f, 631f, 242f); quadTo(650f, 232f, 666f, 214f) },gold,1.2f,.5f)
        arrayOf(floatArrayOf(343f,225f,-30f),floatArrayOf(352f,234f,55f),floatArrayOf(363f,238f,30f),floatArrayOf(373f,242f,-53f),floatArrayOf(382f,245f,-22f),floatArrayOf(391f,250f,62f),floatArrayOf(401f,255f,32f),floatArrayOf(414f,266f,-34f),floatArrayOf(430f,277f,28f),floatArrayOf(657f,225f,30f),floatArrayOf(648f,234f,-55f),floatArrayOf(637f,238f,-30f),floatArrayOf(627f,242f,53f),floatArrayOf(618f,245f,22f),floatArrayOf(609f,250f,-62f),floatArrayOf(599f,255f,-32f),floatArrayOf(586f,266f,34f),floatArrayOf(570f,277f,-28f)).forEachIndexed { i,a -> leaf(c,a[0],a[1],a[2],if(i%3==0).74f else .55f,if(i%4==0)0xffffedd1.toInt() else blue,glow=.5f) }
        butterfly(c,500f,330f,1.15f)
    }
    private fun garden(c:Canvas) {
        stroke(c,Path().apply { moveTo(82f, 1291f); cubicTo(21f, 1169f, 78f, 1131f, 107f, 1077f); moveTo(918f, 1291f); cubicTo(979f, 1169f, 922f, 1131f, 893f, 1077f); moveTo(68f, 707f); cubicTo(22f, 640f, 92f, 570f, 107f, 510f); moveTo(932f, 707f); cubicTo(978f, 640f, 908f, 570f, 893f, 510f) },0xffb3e6d3.toInt(),1f,.25f)
        stroke(c,Path().apply { moveTo(78f, 34f); quadTo(38f, 97f, 58f, 205f); quadTo(87f, 268f, 54f, 354f); moveTo(922f, 34f); quadTo(962f, 97f, 942f, 205f); quadTo(913f, 268f, 946f, 354f); moveTo(88f, 1436f); quadTo(126f, 1498f, 193f, 1544f); quadTo(272f, 1592f, 332f, 1684f); moveTo(912f, 1436f); quadTo(874f, 1498f, 807f, 1544f); quadTo(728f, 1592f, 668f, 1684f) },0xffb3e6d3.toInt(),1.3f,.25f)
        borderLeaves.forEachIndexed { i,a -> leaf(c,a[0],a[1],a[2],1.25f,if(i%3!=0)blue else gold,glow=.25f);leaf(c,1000-a[0],a[1],-a[2],1.25f,if(i%3!=0)blue else gold,glow=.25f) }
        for(x in listOf(165f,835f)) {
            stroke(c,Path().apply { moveTo(x,12f); lineTo(x,283f) },0xffb3e6d3.toInt(),.7f,.25f)
            stroke(c,circle(x,74f,11f),0xffb3e6d3.toInt(),1f,.25f);stroke(c,circle(x,165f,12f),0xffb3e6d3.toInt(),1f,.25f)
            stroke(c,diamond(x,211f,3f),0xffb3e6d3.toInt(),1f,.25f);star(c,x,264f,10f)
        }
    }
    private fun jewels(c:Canvas) {
        arrayOf(
        floatArrayOf(500f, 472f, 49f),
        floatArrayOf(125f, 831f, 53f),
        floatArrayOf(875f, 831f, 53f),
        floatArrayOf(500f, 1224f, 52f),
        floatArrayOf(500f, 1360f, 61f)
    ).forEachIndexed { i,a ->
            val x=a[0];val y=a[1];val r=a[2]
            stroke(c,circle(x,y,r),gold,1.25f,.46f);stroke(c,circle(x,y,r-9),gold,.6f,.46f)
            if(i==1||i==2)at(c,x,y+12) {
                leaf(c,0f,0f,0f,1.15f,blue);leaf(c,0f,10f,-42f,.83f,blue);leaf(c,0f,10f,42f,.83f,blue)
                stroke(c,Path().apply { moveTo(0f, 19f); lineTo(0f, -29f) },0xffe9fbff.toInt(),1.2f,.46f)
            } else stroke(c,diamond(x,y,r*.63f),gold,1.25f,.46f)
        }
        stroke(c,circle(500f,1360f,105f),0xff9deaff.toInt(),1.2f,.46f);stroke(c,circle(500f,1360f,135f),gold,.7f,.46f)
        stroke(c,Path().apply { moveTo(500f, 359f); lineTo(500f, 418f); moveTo(500f, 523f); lineTo(500f, 568f); moveTo(500f, 1114f); lineTo(500f, 1172f); moveTo(500f, 1277f); lineTo(500f, 1295f); moveTo(500f, 1422f); lineTo(500f, 1515f) },gold,1.1f,.46f)
    }
    private fun globe(c:Canvas) {
        paint.reset();paint.isAntiAlias=true
        paint.shader=RadialGradient(480.48f,1329.5f,91.5f,intArrayOf(0x3ddfffff,0x1a3ccfff,0x14092b40,0x9977e3ff.toInt(),0xe6e8ffff.toInt()),floatArrayOf(0f,.26f,.72f,.95f,1f),Shader.TileMode.CLAMP)
        c.drawCircle(500f,1360f,61f,paint);paint.shader=null
        stroke(c,circle(500f,1360f,61f),0xff90eaff.toInt(),1.25f,.46f)
        val save=c.save();c.clipPath(circle(500f,1360f,59f));val rng=Random(19)
        repeat(90) { bloom(c,440+rng.nextFloat()*120,1300+rng.nextFloat()*120,3+rng.nextFloat()*15,intArrayOf(0x26b7f8ff,0x00b7f8ff),floatArrayOf(0f,1f)) };c.restoreToCount(save)
        stroke(c,Path().apply { moveTo(457f, 1340f); cubicTo(465f, 1319f, 481f, 1310f, 498f, 1307f); moveTo(453f, 1350f); lineTo(455f, 1346f); moveTo(546f, 1387f); cubicTo(537f, 1401f, 524f, 1409f, 511f, 1412f) },0xffedffff.toInt(),2f,.46f,204)
        stroke(c,Path().apply { moveTo(452f, 1345f); cubicTo(503f, 1371f, 463f, 1393f, 519f, 1405f); moveTo(472f, 1312f); cubicTo(506f, 1347f, 540f, 1329f, 550f, 1361f); moveTo(473f, 1407f); cubicTo(482f, 1373f, 531f, 1381f, 546f, 1334f); moveTo(447f, 1364f); cubicTo(470f, 1387f, 508f, 1327f, 535f, 1317f) },0xff90eaff.toInt(),.5f,.46f,191)
        stroke(c,Path().apply { moveTo(476f, 1311f); lineTo(479f, 1328f); lineTo(464f, 1345f); lineTo(483f, 1349f); lineTo(479f, 1365f); lineTo(491f, 1376f); lineTo(488f, 1392f); lineTo(504f, 1408f); moveTo(514f, 1313f); lineTo(521f, 1331f); lineTo(507f, 1343f); lineTo(515f, 1359f); lineTo(534f, 1371f); lineTo(523f, 1387f); lineTo(528f, 1405f) },0xffbdfaff.toInt(),1.7f,.46f)
        arrayOf(476f to 1311f,464f to 1345f,491f to 1376f,504f to 1408f,521f to 1331f,515f to 1359f,523f to 1387f).forEach { fill(c,circle(it.first,it.second,2.2f),0xffb5f8ff.toInt()) }
        stroke(c,Path().apply { addArc(367f,1227f,633f,1493f,32f,(126/(2*PI*133)*360).toFloat()) },0xff96efff.toInt(),3f,.46f)
        // 126+706 is slightly shorter than this circumference: retain the tiny second dash.
        stroke(c,Path().apply { addArc(367f,1227f,633f,1493f,(32+832/(2*PI*133)*360).toFloat(),
            ((2*PI*133-832)/(2*PI*133)*360).toFloat()) },0xff96efff.toInt(),3f,.46f)
        stroke(c,Path().apply { moveTo(392f, 1438f); quadTo(415f, 1474f, 457f, 1486f); moveTo(470f, 1490f); quadTo(512f, 1495f, 547f, 1484f); moveTo(572f, 1473f); lineTo(578f, 1469f) },0xffd1fbff.toInt(),1.2f,.46f)
        stroke(c,Path().apply { moveTo(389f, 1429f); cubicTo(418f, 1486f, 506f, 1518f, 581f, 1459f); moveTo(412f, 1458f); cubicTo(447f, 1487f, 504f, 1500f, 543f, 1487f) },0xff7deeff.toInt(),15f,.46f,32)
    }
    private fun glints(c:Canvas) {
        arrayOf(185f to 725f,815f to 725f,280f to 613f,720f to 613f,200f to 1035f,800f to 1035f,245f to 746f,755f to 746f,301f to 916f,699f to 916f).forEachIndexed { i,p ->
            cyan(c,p.first,p.second,if(i%2==1)29f else 23f);stroke(c,circle(p.first,p.second,if(i<4)14f else 8f),0xffd9ffff.toInt(),1.2f,.8f)
            fill(c,circle(p.first,p.second,2.8f),0xffe7fcff.toInt());ray(c,p.first,p.second,12f,0xffbcf8ff.toInt())
        }
        cyan(c,500f,ArtworkGeometry.W03_ROOT_Y,94f)
        stroke(c,Path().apply { moveTo(500f, 1011f); lineTo(518f, 1062f); lineTo(552f, 1083f); lineTo(518f, 1102f); lineTo(500f, 1160f); lineTo(483f, 1101f); lineTo(448f, 1083f); lineTo(483f, 1063f); close(); moveTo(500f, 1038f); lineTo(509f, 1071f); lineTo(527f, 1083f); lineTo(509f, 1095f); lineTo(500f, 1128f); lineTo(491f, 1095f); lineTo(473f, 1083f); lineTo(491f, 1071f); close() },0xffc6faff.toInt(),1.1f,.8f)
        stroke(c,Path().apply { moveTo(483f, 969f); cubicTo(469f, 1018f, 486f, 1033f, 455f, 1089f); cubicTo(447f, 1104f, 441f, 1119f, 443f, 1130f); moveTo(517f, 969f); cubicTo(531f, 1018f, 514f, 1033f, 545f, 1089f); cubicTo(553f, 1104f, 559f, 1119f, 557f, 1130f) },0xffb5f6ff.toInt(),1f,.8f)
        cyan(c,500f,170f,63f)
    }
    private fun fixedStars(c:Canvas) {
        arrayOf(floatArrayOf(500f,45f,17f),floatArrayOf(500f,92f,13f),floatArrayOf(500f,170f,43f),floatArrayOf(500f,394f,22f),floatArrayOf(500f,472f,31f),floatArrayOf(35f,831f,25f),floatArrayOf(965f,831f,25f),floatArrayOf(500f,864f,17f),floatArrayOf(500f,1083f,41f),floatArrayOf(500f,1224f,30f),floatArrayOf(500f,1305f,18f),floatArrayOf(500f,1480f,20f),floatArrayOf(500f,1532f,32f),floatArrayOf(500f,1678f,21f)).forEachIndexed { i,a ->
            val x=a[0];val y=a[1];val r=a[2]
            bloom(c,x,y,minOf(r*1.1f,x-5,995-x),intArrayOf(0x3dfffef1,0x31ffe2a3,0x00edaa40),floatArrayOf(0f,.15f,1f))
            ray(c,x,y,r,if(i==2||i==8)0xffd5fcff.toInt() else 0xfffff5d4.toInt())
            if(r>25)at(c,x,y,45f) { ray(c,0f,0f,r*.55f,0xffe8fbff.toInt()) }
        }
        for((y,r) in listOf(472f to 32f,1224f to 32f,1532f to 35f)) stroke(c,Path().apply {
            moveTo(500f,y-r);quadTo(504f,y-4,500+r*.7f,y);quadTo(504f,y+4,500f,y+r)
            quadTo(496f,y+4,500-r*.7f,y);quadTo(496f,y-4,500f,y-r);close()
        },0xfffff7dc.toInt(),1.2f,1f)
    }
    private fun medallion(c:Canvas) {
        fill(c,circle(500f,864f,20f),0xffe7c974.toInt());stroke(c,circle(500f,864f,20f),0xfffffdeb.toInt(),1.4f,.75f)
        stroke(c,circle(500f,864f,16f),0xfffffdeb.toInt(),1.4f,.75f)
        repeat(8) { i -> val p=polar(3f,i*45f,500f,864f);val q=polar(15f,i*45f,500f,864f)
            stroke(c,Path().apply { moveTo(p.first,p.second);lineTo(q.first,q.second) },0xfffffdeb.toInt(),1.1f,.75f) }
        fill(c,circle(500f,864f,3.2f),0xffffffee.toInt())
    }
    private fun drawAtmosphere(c:Canvas) {
        c.drawColor(0xff02090e.toInt())
        bloom(c,500f,860f,490f,intArrayOf(0x36126c91,0x0b126c91,0x00126c91),floatArrayOf(0f,.72f,1f))
        // Canvas turbulence approximation: seeded multiscale cloudlets follow the authored veils.
        // ponytail: gradient cloudlets approximate SVG displacement; tune against captures before shader work.
        val random=Random(27)
        veils.forEachIndexed { i,p ->
            val measure=android.graphics.PathMeasure(p,false);val point=FloatArray(2)
            repeat(150) { j -> measure.getPosTan(measure.length*j/149,point,null)
                val spread=if(i>=4)75f else if(i%2==1)25f else 57.5f
                bloom(c,point[0]+(random.nextFloat()-.5f)*spread,point[1]+(random.nextFloat()-.5f)*spread,
                    10+random.nextFloat()*spread,intArrayOf(0x1484dfff,0x092980c2,0x002980c2),floatArrayOf(0f,.5f,1f))
            }
            stroke(c,p,if(i%2==1)0xff81e7ff.toInt() else 0xffb2eaff.toInt(),if(i%2==1).8f else 2.4f,0f,77)
        }
        val mist=Path().apply { moveTo(302f, 1060f); cubicTo(181f, 933f, 266f, 639f, 469f, 567f); moveTo(689f, 1087f); cubicTo(831f, 951f, 747f, 644f, 545f, 589f) }
        stroke(c,mist,0xffa6edff.toInt(),75f,1f,12)
        fun randomAt(i:Int):Double { val v=sin(i*127.1+19)*43758.5453; return v-floor(v) }
        repeat(3200) { i -> val lower=i%3!=0;val t=randomAt(i+1);val side=if(i%2==0)1 else -1
            val y=if(lower)1290+t*488 else t*500;val center=if(lower)80+t*225 else 75+t*205
            val x=500+side*(500-center)+(randomAt(i+11)-.5)*(if(lower)170 else 125)
            fill(c,circle(round3(x.toFloat()),round3(y.toFloat()),(.35+randomAt(i+31)*(if(lower)1.8 else 1.1)).toFloat()),
                if(i%6==0)0xfffff7d8.toInt() else 0xff8fdffc.toInt(),((.15+randomAt(i+23)*.65)*255).toInt())
        }
        for(sign in listOf(1f,-1f))at(c,if(sign<0)1000f else 0f,0f,0f,sign,1f) {
            val ribbon=Path().apply { moveTo(238f, -15f); cubicTo(237f, 62f, 146f, 111f, 95f, 163f); cubicTo(38f, 221f, 60f, 273f, 11f, 322f); cubicTo(45f, 244f, 17f, 218f, 63f, 154f); cubicTo(108f, 92f, 202f, 56f, 238f, -15f); close(); moveTo(-4f, 1440f); cubicTo(37f, 1501f, 112f, 1513f, 148f, 1593f); cubicTo(181f, 1668f, 180f, 1732f, 251f, 1785f); cubicTo(191f, 1757f, 156f, 1690f, 126f, 1620f); cubicTo(93f, 1544f, 41f, 1542f, -4f, 1440f); close() }
            fill(c,ribbon,0xff75e6f2.toInt(),33);stroke(c,ribbon,0xff9bedff.toInt(),.8f,0f,107)
            stroke(c,Path().apply { moveTo(-11f, 1461f); cubicTo(89f, 1596f, 101f, 1746f, 288f, 1770f); moveTo(-20f, 1402f); cubicTo(57f, 1581f, 77f, 1722f, 198f, 1777f); moveTo(251f, -10f); cubicTo(216f, 61f, 108f, 111f, 63f, 213f); moveTo(203f, -7f); cubicTo(173f, 81f, 82f, 130f, 46f, 226f) },0xffb1f7ff.toInt(),1.2f,0f,148)
        }
        repeat(520) { i -> val x=round3((24+(i*167.39)%952).toFloat());val y=round3((24+(i*257.71)%1730).toFloat())
            fill(c,circle(x,y,if(i%19==0)1.65f else if(i%4==0)1f else .5f),if(i%3==0)0xffe5faff.toInt() else 0xff7bdcfa.toInt(),((.2+i%7*.095)*255).toInt()) }
    }
}
