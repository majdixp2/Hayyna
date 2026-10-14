package com.example.data.model

data class RegionItem(
    val name: String,
    val districts: List<DistrictItem>
)

data class DistrictItem(
    val name: String,
    val latitude: Double,
    val longitude: Double
)

data class PromoVideoSample(
    val title: String,
    val url: String,
    val duration: String
)

object LocationPresets {
    val regions = listOf(
        RegionItem(
            name = "منطقة الرياض",
            districts = listOf(
                DistrictItem("حي الملقا", 24.8142, 46.6025),
                DistrictItem("حي العليا", 24.7136, 46.6753),
                DistrictItem("حي النخيل", 24.7578, 46.6438),
                DistrictItem("حي حطين", 24.7725, 46.6012),
                DistrictItem("حي الروضة", 24.7431, 46.7729),
                DistrictItem("حي الياسمين", 24.8251, 46.6417)
            )
        ),
        RegionItem(
            name = "منطقة مكة المكرمة - جدة",
            districts = listOf(
                DistrictItem("حي الروضة", 21.5714, 39.1558),
                DistrictItem("حي الشاطئ", 21.6025, 39.1129),
                DistrictItem("حي الزهراء", 21.5891, 39.1352),
                DistrictItem("حي الحمراء", 21.5167, 39.1625)
            )
        ),
        RegionItem(
            name = "المنطقة الشرقية - الخبر والدمام",
            districts = listOf(
                DistrictItem("حي الحزام الذهبي (الخبر)", 26.2942, 50.2078),
                DistrictItem("حي الشاطئ الغربي", 26.4632, 50.1172),
                DistrictItem("حي الفاخرية", 26.3981, 50.1245)
            )
        ),
        RegionItem(
            name = "منطقة المدينة المنورة",
            districts = listOf(
                DistrictItem("حي سلطانة", 24.4851, 39.5982),
                DistrictItem("حي العريض", 24.4782, 39.6389)
            )
        )
    )

    val cuisines = listOf(
        "الكل",
        "برجر وساندوتش 🍔",
        "بيتزا وإيطالي 🍕",
        "مشاوي وشاورما 🍢",
        "مأكولات شعبية وكبسة 🍗",
        "مقاهي وحلويات ☕",
        "مأكولات بحرية 🐟"
    )

    val categories = listOf(
        "الكل",
        "برجر وساندوتش 🍔",
        "بيتزا وإيطالي 🍕",
        "مشاوي وشاورما 🍢",
        "مأكولات شعبية وكبسة 🍗",
        "مقاهي وحلويات ☕",
        "مأكولات بحرية 🐟"
    )

    val offerBadges = listOf(
        "فيديو إعلاني حصري 🎬",
        "خصم 50% نصف السعر 🔥",
        "اشترِ وجبة واحصل على الثانية مجاناً 🎁",
        "عرض الغداء الملكي 🍽️",
        "بوكس الويكند العائلي 🌟",
        "توصيل ومشروب مجاني 🥤"
    )

    val promoVideos = listOf(
        PromoVideoSample(
            title = "فيديو إعلاني ترويجي 1",
            url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
            duration = "0:15"
        ),
        PromoVideoSample(
            title = "فيديو إعلاني ترويجي 2",
            url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
            duration = "0:15"
        )
    )
}
