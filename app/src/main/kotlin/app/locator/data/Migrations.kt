package app.locator.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Phase 2: a record may carry a GPS fix and a photo. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE LocationRecord ADD COLUMN latitude REAL")
        db.execSQL("ALTER TABLE LocationRecord ADD COLUMN longitude REAL")
        db.execSQL("ALTER TABLE LocationRecord ADD COLUMN accuracyMeters REAL")
        db.execSQL("ALTER TABLE LocationRecord ADD COLUMN photoPath TEXT")
    }
}

/** Phase 3: reminders. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE LocationRecord ADD COLUMN remindAt INTEGER")
    }
}

/**
 * Phase 4: places. Existing records keep their text and simply have no place (placeId stays null), so nothing
 * the user saved changes; they can be filed under a place later.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `Place` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                "`parentId` INTEGER, FOREIGN KEY(`parentId`) REFERENCES `Place`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_Place_parentId` ON `Place` (`parentId`)")
        db.execSQL(
            "ALTER TABLE `LocationRecord` ADD COLUMN `placeId` INTEGER " +
                "REFERENCES `Place`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_LocationRecord_placeId` ON `LocationRecord` (`placeId`)")
    }
}

/** Usual place: where an item normally lives, so it can be put back and flagged when it is elsewhere. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE `Item` ADD COLUMN `usualPlaceId` INTEGER " +
                "REFERENCES `Place`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL",
        )
        db.execSQL("ALTER TABLE `Item` ADD COLUMN `usualText` TEXT")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_Item_usualPlaceId` ON `Item` (`usualPlaceId`)")
    }
}
