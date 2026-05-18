package com.github.cfogrady.vitalwear.transfer

import android.content.Context
import com.github.cfogrady.vb.dim.sprite.SpriteData
import com.github.cfogrady.vitalwear.character.VBCharacter
import com.github.cfogrady.vitalwear.common.card.CardType
import com.github.cfogrady.vitalwear.common.card.CharacterSpritesIO
import com.github.cfogrady.vitalwear.common.card.SpriteFileIO
import com.github.cfogrady.vitalwear.common.card.db.CardMetaEntity
import com.github.cfogrady.vitalwear.common.card.db.CardMetaEntityDao
import com.github.cfogrady.vitalwear.common.card.db.SpeciesEntity
import com.github.cfogrady.vitalwear.common.card.db.SpeciesEntityDao
import com.github.cfogrady.vitalwear.protos.Character
import com.google.protobuf.ByteString
import java.io.File

internal fun VBCharacter.buildTransferCardMetadata(): Character.TransferCard {
    return Character.TransferCard.newBuilder()
        .setCardId(cardMeta.cardId)
        .setCardName(cardName())
        .setIsBem(cardMeta.cardType == CardType.BEM)
        .setStageCount(cardMeta.maxAdventureCompletion?.plus(1) ?: 0)
        .setFranchise(cardMeta.franchise)
        .build()
}

internal fun VBCharacter.buildTransferSpeciesMetadata(context: Context): Character.TransferSpecies? {
    val spriteDir = speciesStats.spriteDirName
    val nameSprite = loadTransferSprite(context, spriteDir, CharacterSpritesIO.NAME) ?: return null
    val idle1 = loadTransferSprite(context, spriteDir, CharacterSpritesIO.IDLE1) ?: return null
    val idle2 = loadTransferSprite(context, spriteDir, CharacterSpritesIO.IDLE2, CharacterSpritesIO.IDLE1) ?: idle1
    val walk1 = loadTransferSprite(context, spriteDir, CharacterSpritesIO.WALK1, CharacterSpritesIO.IDLE1) ?: idle1
    val walk2 = loadTransferSprite(context, spriteDir, CharacterSpritesIO.WALK2, CharacterSpritesIO.IDLE1) ?: idle1
    val run1 = loadTransferSprite(context, spriteDir, CharacterSpritesIO.RUN1, CharacterSpritesIO.IDLE1) ?: idle1
    val run2 = loadTransferSprite(context, spriteDir, CharacterSpritesIO.RUN2, CharacterSpritesIO.IDLE2) ?: idle2
    val train1 = loadTransferSprite(context, spriteDir, CharacterSpritesIO.TRAIN1, CharacterSpritesIO.IDLE1) ?: idle1
    val train2 = loadTransferSprite(context, spriteDir, CharacterSpritesIO.TRAIN2, CharacterSpritesIO.IDLE2) ?: idle2
    val win = loadTransferSprite(context, spriteDir, CharacterSpritesIO.WIN, CharacterSpritesIO.IDLE1) ?: idle1
    val down = loadTransferSprite(context, spriteDir, CharacterSpritesIO.DOWN, CharacterSpritesIO.IDLE2) ?: idle2
    val attack = loadTransferSprite(context, spriteDir, CharacterSpritesIO.ATTACK, CharacterSpritesIO.WALK1) ?: walk1
    val dodge = loadTransferSprite(context, spriteDir, CharacterSpritesIO.DODGE, CharacterSpritesIO.IDLE1) ?: idle1
    val splash = loadTransferSprite(context, spriteDir, CharacterSpritesIO.SPLASH, CharacterSpritesIO.IDLE1) ?: idle1

    return Character.TransferSpecies.newBuilder()
        .setSlotId(characterStats.slotId)
        .setStage(speciesStats.phase)
        .setAttribute(speciesStats.attribute)
        .setBaseHp(speciesStats.hp)
        .setBaseBp(speciesStats.bp)
        .setBaseAp(speciesStats.ap)
        .setNameSprite(ByteString.copyFrom(nameSprite.pixelData))
        .setNameSpriteWidth(nameSprite.width)
        .setNameSpriteHeight(nameSprite.height)
        .setIdle1(ByteString.copyFrom(idle1.pixelData))
        .setIdle2(ByteString.copyFrom(idle2.pixelData))
        .setWalk1(ByteString.copyFrom(walk1.pixelData))
        .setWalk2(ByteString.copyFrom(walk2.pixelData))
        .setRun1(ByteString.copyFrom(run1.pixelData))
        .setRun2(ByteString.copyFrom(run2.pixelData))
        .setTrain1(ByteString.copyFrom(train1.pixelData))
        .setTrain2(ByteString.copyFrom(train2.pixelData))
        .setWin(ByteString.copyFrom(win.pixelData))
        .setDown(ByteString.copyFrom(down.pixelData))
        .setAttack(ByteString.copyFrom(attack.pixelData))
        .setDodge(ByteString.copyFrom(dodge.pixelData))
        .setSplash(ByteString.copyFrom(splash.pixelData))
        .setSpriteWidth(idle1.width)
        .setSpriteHeight(idle1.height)
        .build()
}

internal fun Character.resolveImportedCardMetaOrPlaceholder(
    context: Context,
    cardMetaEntityDao: CardMetaEntityDao,
    speciesEntityDao: SpeciesEntityDao,
): CardMetaEntity? {
    val matchedCard = resolveImportedCardMeta(cardMetaEntityDao, speciesEntityDao)
    if (matchedCard != null) {
        ensureTransferSpeciesExists(context, matchedCard, speciesEntityDao)
        return matchedCard
    }

    if (!hasTransferCard() || !hasTransferSpecies()) {
        return null
    }

    val transferCard = transferCard
    val baseName = transferCard.cardName
        .takeIf { it.isNotBlank() }
        ?: cardName.takeIf { it.isNotBlank() }
        ?: "Transferred Card ${transferCard.cardId}"
    val placeholderName = buildPlaceholderCardName(cardMetaEntityDao, baseName, transferCard.cardId)
    val placeholderCard = cardMetaEntityDao.getByName(placeholderName) ?: CardMetaEntity(
        cardName = placeholderName,
        cardId = transferCard.cardId,
        cardChecksum = 0,
        cardType = if (transferCard.isBem) CardType.BEM else CardType.DIM,
        franchise = transferCard.franchise,
        maxAdventureCompletion = null,
    ).also { cardMetaEntityDao.insert(it) }

    ensureTransferSpeciesExists(context, placeholderCard, speciesEntityDao)
    return cardMetaEntityDao.getByName(placeholderName) ?: placeholderCard
}

private fun Character.ensureTransferSpeciesExists(
    context: Context,
    matchedCard: CardMetaEntity,
    speciesEntityDao: SpeciesEntityDao,
) {
    if (!hasTransferSpecies()) {
        return
    }

    val transferSpecies = transferSpecies
    val existing = runCatching {
        speciesEntityDao.getCharacterByCardAndCharacterId(matchedCard.cardName, transferSpecies.slotId)
    }.getOrNull()
    if (existing != null) {
        return
    }

    val spriteDirName = buildTransferSpriteDir(matchedCard.cardName, matchedCard.cardId, transferSpecies.slotId)
    saveTransferSprite(context, spriteDirName, CharacterSpritesIO.NAME, transferSpecies.nameSprite.toByteArray(), transferSpecies.nameSpriteWidth, transferSpecies.nameSpriteHeight)
    saveTransferSprite(context, spriteDirName, CharacterSpritesIO.IDLE1, transferSpecies.idle1.toByteArray(), transferSpecies.spriteWidth, transferSpecies.spriteHeight)
    saveTransferSprite(context, spriteDirName, CharacterSpritesIO.IDLE2, firstNonEmpty(transferSpecies.idle2.toByteArray(), transferSpecies.idle1.toByteArray()), transferSpecies.spriteWidth, transferSpecies.spriteHeight)
    saveTransferSprite(context, spriteDirName, CharacterSpritesIO.WALK1, firstNonEmpty(transferSpecies.walk1.toByteArray(), transferSpecies.idle1.toByteArray()), transferSpecies.spriteWidth, transferSpecies.spriteHeight)
    saveTransferSprite(context, spriteDirName, CharacterSpritesIO.WALK2, firstNonEmpty(transferSpecies.walk2.toByteArray(), transferSpecies.idle1.toByteArray()), transferSpecies.spriteWidth, transferSpecies.spriteHeight)
    saveTransferSprite(context, spriteDirName, CharacterSpritesIO.RUN1, firstNonEmpty(transferSpecies.run1.toByteArray(), transferSpecies.idle1.toByteArray()), transferSpecies.spriteWidth, transferSpecies.spriteHeight)
    saveTransferSprite(context, spriteDirName, CharacterSpritesIO.RUN2, firstNonEmpty(transferSpecies.run2.toByteArray(), transferSpecies.idle2.toByteArray(), transferSpecies.idle1.toByteArray()), transferSpecies.spriteWidth, transferSpecies.spriteHeight)
    saveTransferSprite(context, spriteDirName, CharacterSpritesIO.TRAIN1, firstNonEmpty(transferSpecies.train1.toByteArray(), transferSpecies.idle1.toByteArray()), transferSpecies.spriteWidth, transferSpecies.spriteHeight)
    saveTransferSprite(context, spriteDirName, CharacterSpritesIO.TRAIN2, firstNonEmpty(transferSpecies.train2.toByteArray(), transferSpecies.idle2.toByteArray(), transferSpecies.idle1.toByteArray()), transferSpecies.spriteWidth, transferSpecies.spriteHeight)
    saveTransferSprite(context, spriteDirName, CharacterSpritesIO.WIN, firstNonEmpty(transferSpecies.win.toByteArray(), transferSpecies.idle1.toByteArray()), transferSpecies.spriteWidth, transferSpecies.spriteHeight)
    saveTransferSprite(context, spriteDirName, CharacterSpritesIO.DOWN, firstNonEmpty(transferSpecies.down.toByteArray(), transferSpecies.idle2.toByteArray(), transferSpecies.idle1.toByteArray()), transferSpecies.spriteWidth, transferSpecies.spriteHeight)
    saveTransferSprite(context, spriteDirName, CharacterSpritesIO.ATTACK, firstNonEmpty(transferSpecies.attack.toByteArray(), transferSpecies.walk1.toByteArray(), transferSpecies.idle1.toByteArray()), transferSpecies.spriteWidth, transferSpecies.spriteHeight)
    saveTransferSprite(context, spriteDirName, CharacterSpritesIO.DODGE, firstNonEmpty(transferSpecies.dodge.toByteArray(), transferSpecies.idle1.toByteArray()), transferSpecies.spriteWidth, transferSpecies.spriteHeight)
    saveTransferSprite(context, spriteDirName, CharacterSpritesIO.SPLASH, firstNonEmpty(transferSpecies.splash.toByteArray(), transferSpecies.idle1.toByteArray()), transferSpecies.spriteWidth, transferSpecies.spriteHeight)

    speciesEntityDao.insertAll(
        listOf(
            SpeciesEntity(
                cardName = matchedCard.cardName,
                characterId = transferSpecies.slotId,
                phase = transferSpecies.stage,
                attribute = transferSpecies.attribute,
                type = 0,
                attackId = 0,
                criticalAttackId = 0,
                dpStars = 0,
                bp = transferSpecies.baseBp,
                hp = transferSpecies.baseHp,
                ap = transferSpecies.baseAp,
                battlePool1 = 0,
                battlePool2 = 0,
                battlePool3 = 0,
                spriteDirName = spriteDirName,
                raised = transferSpecies.stage > 0,
            )
        )
    )
}

private fun buildPlaceholderCardName(
    cardMetaEntityDao: CardMetaEntityDao,
    baseName: String,
    cardId: Int,
): String {
    val trimmedName = baseName.trim().ifBlank { "Transferred Card" }
    if (cardMetaEntityDao.getByName(trimmedName) == null) {
        return trimmedName
    }
    return if (cardId > 0) "$trimmedName [Transfer $cardId]" else "$trimmedName [Transfer]"
}

private fun buildTransferSpriteDir(cardName: String, cardId: Int, slotId: Int): String {
    val normalizedCardName = cardName.lowercase().filter { it.isLetterOrDigit() }.ifBlank { "transfer" }
    return "transfer_${normalizedCardName}_${cardId}_$slotId"
}

private fun firstNonEmpty(vararg candidates: ByteArray): ByteArray {
    return candidates.firstOrNull { it.isNotEmpty() } ?: byteArrayOf()
}

private fun loadTransferSprite(
    context: Context,
    spriteDir: String,
    spriteFile: String,
    backupSpriteFile: String? = null,
): SpriteData.Sprite? {
    val spriteFileIO = SpriteFileIO()
    val baseDir = File(context.filesDir, "${SpriteFileIO.LIBRARY_DIR}/${CharacterSpritesIO.CHARACTERS}/$spriteDir")
    val primary = File(baseDir, spriteFile)
    if (primary.exists()) {
        return spriteFileIO.loadSpriteFile(primary)
    }
    if (backupSpriteFile != null) {
        val backup = File(baseDir, backupSpriteFile)
        if (backup.exists()) {
            return spriteFileIO.loadSpriteFile(backup)
        }
    }
    return null
}

private fun saveTransferSprite(
    context: Context,
    spriteDir: String,
    spriteFile: String,
    pixelData: ByteArray,
    width: Int,
    height: Int,
) {
    if (pixelData.isEmpty() || width <= 0 || height <= 0) {
        return
    }

    val sprite = SpriteData.Sprite.builder()
        .width(width)
        .height(height)
        .pixelData(pixelData)
        .build()
    val spriteFileIO = SpriteFileIO()
    val file = File(context.filesDir, "${SpriteFileIO.LIBRARY_DIR}/${CharacterSpritesIO.CHARACTERS}/$spriteDir/$spriteFile")
    file.parentFile?.mkdirs()
    spriteFileIO.saveSpriteFile(sprite, file)
}


