package com.github.cfogrady.vitalwear.character.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import timber.log.Timber
import java.time.Duration
import java.time.LocalDateTime

@Entity(tableName = "character")
data class CharacterEntity (
    //TODO: Change to long
    @PrimaryKey(autoGenerate = true)
    var id: Int,
    @ColumnInfo(name = "state") var state: CharacterState,
    @ColumnInfo(name = "card_file") var cardFile: String,
    @ColumnInfo(name = "slot_id") var slotId: Int,
    @ColumnInfo(name = "last_update") var lastUpdate: LocalDateTime,
    @ColumnInfo(name = "vitals") var vitals: Int,
    @ColumnInfo(name = "training_time_remaining") var trainingTimeRemainingInSeconds: Long,
    @ColumnInfo(name = "has_transformations") var hasTransformations: Boolean,
    @ColumnInfo(name = "time_until_next_transformation") var timeUntilNextTransformation: Long,
    @ColumnInfo(name = "trained_bp") var trainedBp: Int,
    @ColumnInfo(name = "trained_hp") var trainedHp: Int,
    @ColumnInfo(name = "trained_ap") var trainedAp: Int,
    @ColumnInfo(name = "trained_pp") var trainedPP: Int,
    // currently injured
    @ColumnInfo(name = "injured") var injured: Boolean,
    // battles lost while injured (planned future field)
    @ColumnInfo(name = "lost_battles_injured") var lostBattlesInjured: Int,
    @ColumnInfo(name = "accumulated_daily_injuries") var accumulatedDailyInjuries: Int,
    @ColumnInfo(name = "total_battles") var totalBattles: Int,
    @ColumnInfo(name = "current_phase_battles") var currentPhaseBattles: Int,
    @ColumnInfo(name = "total_wins") var totalWins: Int,
    @ColumnInfo(name = "current_phase_wins") var currentPhaseWins: Int,
    @ColumnInfo(name = "mood") var mood: Int,
    @ColumnInfo(name = "sleeping", defaultValue = "false") var sleeping: Boolean,
    @ColumnInfo(name = "dead") var dead: Boolean,
    // HCE and cross-device stats for full transfer support
    @ColumnInfo(name = "age_in_days", defaultValue = "0") var ageInDays: Int,
    @ColumnInfo(name = "activity_level", defaultValue = "0") var activityLevel: Int,
    @ColumnInfo(name = "heart_rate_current", defaultValue = "0") var heartRateCurrent: Int,
    @ColumnInfo(name = "generation", defaultValue = "0") var generation: Int,
    @ColumnInfo(name = "total_trophies", defaultValue = "0") var totalTrophies: Int,
    @ColumnInfo(name = "next_adventure_mission_stage", defaultValue = "0") var nextAdventureMissionStage: Int,
    @ColumnInfo(name = "ability_type", defaultValue = "0") var abilityType: Int,
    @ColumnInfo(name = "ability_branch", defaultValue = "0") var abilityBranch: Int,
    @ColumnInfo(name = "ability_rarity", defaultValue = "0") var abilityRarity: Int,
    @ColumnInfo(name = "ability_reset", defaultValue = "0") var abilityReset: Int,
    @ColumnInfo(name = "rank", defaultValue = "0") var rank: Int,
    @ColumnInfo(name = "item_effect_mental_state_value", defaultValue = "0") var itemEffectMentalStateValue: Int,
    @ColumnInfo(name = "item_effect_mental_state_minutes_remaining", defaultValue = "0") var itemEffectMentalStateMinutesRemaining: Int,
    @ColumnInfo(name = "item_effect_activity_level_value", defaultValue = "0") var itemEffectActivityLevelValue: Int,
    @ColumnInfo(name = "item_effect_activity_level_minutes_remaining", defaultValue = "0") var itemEffectActivityLevelMinutesRemaining: Int,
    @ColumnInfo(name = "item_effect_vital_points_change_value", defaultValue = "0") var itemEffectVitalPointsChangeValue: Int,
    @ColumnInfo(name = "item_effect_vital_points_change_minutes_remaining", defaultValue = "0") var itemEffectVitalPointsChangeMinutesRemaining: Int,
    @ColumnInfo(name = "item_type", defaultValue = "0") var itemType: Int,
    @ColumnInfo(name = "item_multiplier", defaultValue = "0") var itemMultiplier: Int,
    @ColumnInfo(name = "item_remaining_time", defaultValue = "0") var itemRemainingTime: Int,
    @ColumnInfo(name = "firmware_minor_version", defaultValue = "0") var firmwareMinorVersion: Int,
    @ColumnInfo(name = "firmware_major_version", defaultValue = "0") var firmwareMajorVersion: Int,
) {
    fun currentPhaseWinRatio(): Int {
        if(currentPhaseBattles == 0) {
            return 0
        }
        return (100 * currentPhaseWins) / currentPhaseBattles
    }

    @Synchronized
    fun updateTimeStamps(now: LocalDateTime) {
        val deltaTimeInSeconds = Duration.between(lastUpdate, now).seconds
        if(deltaTimeInSeconds <= 0) {
            Timber.i("Already updated to timestamp. Skipping update")
            return
        }
        trainingTimeRemainingInSeconds -= deltaTimeInSeconds
        if(trainingTimeRemainingInSeconds < 0) {
            trainingTimeRemainingInSeconds = 0
        }
        timeUntilNextTransformation -= deltaTimeInSeconds
        if(timeUntilNextTransformation < 0) {
            timeUntilNextTransformation = 0
        }
        lastUpdate = now
    }
}