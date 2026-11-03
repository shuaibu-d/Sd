package com.sdtech.studio.data.seed

import android.content.Context
import androidx.room.withTransaction
import com.sdtech.studio.data.local.SDStudioDatabase
import com.sdtech.studio.data.local.entity.BrandKit
import com.sdtech.studio.data.local.entity.CaptionTemplate
import com.sdtech.studio.data.local.entity.HashtagGroup
import com.sdtech.studio.data.model.PaletteColor
import com.sdtech.studio.data.model.StudioDivision
import com.sdtech.studio.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first

class FirstLaunchSeeder(
    private val context: Context,
    private val database: SDStudioDatabase,
    private val settings: SettingsRepository
) {
    suspend fun seedIfNeeded() {
        LogoSeeder.seedIfMissing(context, database)
        if (settings.isInitialSeedComplete.first()) return

        database.withTransaction {
            if (database.brandKitDao().getBrandKit() == null) {
                database.brandKitDao().save(officialBrandKit())
            }
            if (database.captionTemplateDao().count() == 0) {
                database.captionTemplateDao().insertAll(defaultCaptionTemplates())
            }
            if (database.hashtagGroupDao().count() == 0) {
                database.hashtagGroupDao().insertAll(defaultHashtagGroups())
            }
        }
        settings.markInitialSeedComplete()
    }

    private fun officialBrandKit() = BrandKit(
        name = "SD Technologies",
        slogan = "Create Without Limits.",
        palette = listOf(
            PaletteColor("Midnight Navy", "#101C2C"),
            PaletteColor("Innovation Teal", "#16A6A0"),
            PaletteColor("Premium Gold", "#D9B45B"),
            PaletteColor("Cloud White", "#F3F5F8"),
            PaletteColor("Slate Grey", "#263445")
        ),
        website = "",
        whatsapp = "",
        handle = "",
        defaultCta = "Create Without Limits.",
        brandLock = true
    )

    private fun defaultCaptionTemplates() = listOf(
        CaptionTemplate(
            title = "Software That Moves Work Forward",
            body = "Reliable software should make everyday work simpler, faster, and more effective. Discover practical digital solutions designed around real business needs. Contact SD Technologies to discuss your next project.",
            division = StudioDivision.SOFTWARE
        ),
        CaptionTemplate(
            title = "AI With Practical Impact",
            body = "Artificial intelligence is most valuable when it solves meaningful problems. Explore how responsible, useful AI can improve workflows, support better decisions, and create new opportunities.",
            division = StudioDivision.AI
        ),
        CaptionTemplate(
            title = "Build Skills for What Comes Next",
            body = "Technology keeps evolving, and continuous learning helps you stay prepared. Strengthen your knowledge with focused training, practical experience, and skills you can apply with confidence.",
            division = StudioDivision.ACADEMY
        ),
        CaptionTemplate(
            title = "Make Your Digital Presence Count",
            body = "A strong digital presence starts with a clear message and a consistent experience. Connect your brand with the right audience through thoughtful strategy, creative content, and effective digital solutions.",
            division = StudioDivision.DIGITAL
        ),
        CaptionTemplate(
            title = "Create Without Limits",
            body = "Ideas become valuable when they are shaped into useful experiences. SD Technologies brings creativity and technology together to help individuals and businesses move forward. Tell us what you want to create.",
            division = StudioDivision.GENERAL
        )
    )

    private fun defaultHashtagGroups() = listOf(
        HashtagGroup(
            name = "Software Solutions",
            tags = listOf("#SDTechnologies", "#SoftwareDevelopment", "#BusinessSoftware", "#DigitalSolutions", "#BuildBetter"),
            division = StudioDivision.SOFTWARE
        ),
        HashtagGroup(
            name = "Artificial Intelligence",
            tags = listOf("#SDTechnologies", "#ArtificialIntelligence", "#AIInnovation", "#ResponsibleAI", "#FutureOfWork"),
            division = StudioDivision.AI
        ),
        HashtagGroup(
            name = "SD Academy",
            tags = listOf("#SDTechnologies", "#SDAcademy", "#SkillsDevelopment", "#LifelongLearning", "#LearnAndGrow"),
            division = StudioDivision.ACADEMY
        ),
        HashtagGroup(
            name = "Digital Services",
            tags = listOf("#SDTechnologies", "#DigitalStrategy", "#DigitalMarketing", "#CreativeContent", "#GrowOnline"),
            division = StudioDivision.DIGITAL
        )
    )
}
