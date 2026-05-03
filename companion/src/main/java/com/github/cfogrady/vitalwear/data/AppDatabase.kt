package com.github.cfogrady.vitalwear.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.github.cfogrady.vitalwear.card.ValidatedCardEntity
import com.github.cfogrady.vitalwear.card.ValidatedCardEntityDao

/**
 * Slimmed-down companion database — holds only validated-card unlock state.
 *
 * Card catalog data (species, transformations, adventures, etc.) has moved exclusively to
 * VBHelper's `internalDb`.  When the companion imports a DIM file it syncs the raw bytes
 * to VBHelper via [com.github.cfogrady.vitalwear.card.VBHelperCardSync], which parses and
 * stores those tables there.
 *
 * Migration 3 → 4: drop the now-redundant card-catalog tables that were never actually
 * populated in production builds.
 */
@Database(
    entities = [ValidatedCardEntity::class],
    version = 4
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun validatedCardEntityDao(): ValidatedCardEntityDao

    companion object {
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Card catalog tables have been moved to VBHelper's single database.
                db.execSQL("DROP TABLE IF EXISTS SpeciesEntity")
                db.execSQL("DROP TABLE IF EXISTS CardMetaEntity")
                db.execSQL("DROP TABLE IF EXISTS TransformationEntity")
                db.execSQL("DROP TABLE IF EXISTS AdventureEntity")
                db.execSQL("DROP TABLE IF EXISTS AttributeFusionEntity")
                db.execSQL("DROP TABLE IF EXISTS SpecificFusionEntity")
            }
        }
    }
}