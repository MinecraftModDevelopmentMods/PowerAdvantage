param([string]$ReferenceRoot = (Join-Path $PSScriptRoot '../../../../PA115/PowerAdvantage/test-rig/client-resources'))
$ErrorActionPreference = 'Stop'
$ReferenceRoot = (Resolve-Path -LiteralPath $ReferenceRoot).Path
$referenceHashes = @{
    ClientResourceProbe = 'CA78566EB69CF10C420856D9D748477361CF8DD1BD9E49A915AE1122C572C73E'
    TankVisualProbe = '756DE3330EDF1A9E649A2CE06BEFD7AC4A1C5E39E4443EE69A0AFE1D33A1807F'
    OilRenderProbe = '3560449BA7DBB2AD60CDEF4FEB5A7DCB53EF66A0AA9A82EC538F4D1993CD8ECE'
    RecipeBookGroupingProbe = 'C9E02B30693C7CCFA024290DE4B286E707BE5DD9BC07633704437FEA4E660376'
}
$checks = 0
function Assert-Contract([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
    $script:checks++
}
function Normalize-Probe([string]$Source) {
    # The accepted 1.15 source is immutable. Only these platform API spellings
    # are equivalent; conditions, quantities, bounds and thresholds are retained.
    $pairs = [ordered]@{
        '1.15.2'='1.16.5'; '31.2.57'='36.2.34';
        '2.4.6.115021'='2.4.7.116051'; '2.2.7.115021'='2.2.8.116051';
        'IFluidState'='FluidState'; 'getTileEntity('='getBlockEntity(';
        '.getDefaultState()'='.defaultBlockState()'; '.getFilledBucket()'='.getBucket()';
        '.getFlowingFluidState('='.getFlowing('; 'state.getFluid()'='state.getType()';
        '.getDescriptionId('=' .getDescriptionId('; '.getTranslationKey('=' .getDescriptionId(';
        'Block.shouldSideBeRendered('='Block.shouldRenderFace('; 'BlockPos.ZERO.up()'='BlockPos.ZERO.above()';
        '.getLoadingGui()'='.getOverlay()'; '.isIntegratedServerRunning()'='.hasSingleplayerServer()';
        'I18n.format('='I18n.get('; '.getItemModelWithOverrides('='.getModel(';
        '.getStateContainer()'='.getStateDefinition()'; '.getValidStates()'='.getPossibleStates()';
        '.getBlockModelShapes().getModel('='.getBlockModelShaper().getBlockModel(';
        'tab_powerAdvantage.fill('='tab_powerAdvantage.fillItemList('; 'tab_powerAdvantage.getIcon()'='tab_powerAdvantage.getIconItem()';
        'minecraft.currentScreen'='minecraft.screen'; 'minecraft.shutdown()'='minecraft.stop()';
        '.getParticleTexture()'='.getParticleTexture(net.minecraftforge.client.model.data.EmptyModelData.INSTANCE)';
        'quad.func_187508_a()'='quad.getSprite()';
        '.getAtlasSpriteGetter('='.getTextureAtlas('; 'AtlasTexture.LOCATION_BLOCKS_TEXTURE'='AtlasTexture.LOCATION_BLOCKS';
        '.getMinU()'='.getU0()'; '.getMaxU()'='.getU1()'; '.getMinV()'='.getV0()'; '.getMaxV()'='.getV1()';
        'state.getBlockState()'='state.createLegacyBlock()';
        '.finishDrawing()'='.end()'; '.getNextBuffer()'='.popNextBuffer()'; '.getVertexCount()'='.vertexCount()';
        'saved.keySet()'='saved.getAllKeys()'; '.readPacketData('='.read('; '.getRecipeOutput()'='.getResultItem()';
        'manager.func_223389_a('='manager.replaceRecipes('; 'new ClientRecipeBook(manager)'='new ClientRecipeBook()';
        'book::unlock'='book::add'; 'book.rebuildTable()'='book.setupCollections(manager.getRecipes())';
        'book.getRecipes()'='book.getCollections()'; 'Minecraft.IS_RUNNING_ON_MAC'='Minecraft.ON_OSX';
        '.bindFramebuffer('='.bindWrite('; '.deleteFramebuffer()'='.destroyBuffers()'; 'minecraft.getFramebuffer()'='minecraft.getMainRenderTarget()';
        'button.render(-1, -1, 1F)'='button.render(new com.mojang.blaze3d.matrix.MatrixStack(), -1, -1, 1F)';
        'minecraft.fontRenderer.drawString('='minecraft.font.draw(new com.mojang.blaze3d.matrix.MatrixStack(), ';
        '.getFormattedText()'='.getString()'; 'createContainer('='createMenu('
    }
    $Source = $Source.Replace("`r`n", "`n")
    foreach ($pair in $pairs.GetEnumerator()) { $Source = $Source.Replace($pair.Key, $pair.Value) }
    $Source = [regex]::Replace($Source, '(?<![\w.])minecraft\.world\b', 'minecraft.level')
    # Block and fluid default states have different native names.
    $Source = [regex]::Replace($Source, '(Fluids\.(?:crude_oil|refined_oil|WATER|LAVA))\.defaultBlockState\(\)', '$1.defaultFluidState()')
    $Source
}
function Canonical-Source([string]$Source) {
    $Source = [regex]::Replace($Source, '(?m)^import [^\r\n]+\r?\n', '')
    $Source = [regex]::Replace($Source, '/\*[\s\S]*?\*/|//[^\r\n]*', '')
    [regex]::Replace($Source, '\s+', '')
}
$relative = 'java/zone/moddev/mc/advantageworks/client'
foreach ($name in 'ClientResourceProbe','TankVisualProbe','OilRenderProbe','RecipeBookGroupingProbe') {
    $sourcePath = Join-Path $ReferenceRoot "$relative/$name.java"
    $hash = (Get-FileHash -LiteralPath $sourcePath -Algorithm SHA256).Hash
    Assert-Contract ($hash -eq $referenceHashes[$name]) "Reference SHA-256 mismatch for $name.java; expected $($referenceHashes[$name]), got $hash. Do not substitute a different baseline."
    $source = Get-Content -LiteralPath $sourcePath -Raw
    $target = Get-Content -LiteralPath (Join-Path $PSScriptRoot "$relative/$name.java") -Raw
    $expected = Normalize-Probe $source
    $actual = Normalize-Probe $target
    # Keep every original assertion, including its operands, thresholds and message.
    foreach ($assertion in [regex]::Matches($expected, '(?s)\brequire\(.*?\);')) {
        Assert-Contract ($actual.Contains($assertion.Value)) "$name lost an original assertion: $($assertion.Value)"
    }
    foreach ($literal in [regex]::Matches($expected, '"[^"\r\n]*(?::|\.png|\.bin|\.dat)[^"\r\n]*"')) {
        Assert-Contract ($actual.Contains($literal.Value)) "$name changed a resource, fixture or diagnostic literal: $($literal.Value)"
    }
    if ($name -eq 'RecipeBookGroupingProbe') {
        $expected = $expected.Replace('import com.mojang.blaze3d.platform.GlStateManager;', 'import com.mojang.blaze3d.systems.RenderSystem;')
        Assert-Contract ((Canonical-Source $actual) -ceq (Canonical-Source $expected)) 'Recipe grouping/capture changed beyond the explicit native API adaptation.'
    }
    if ($name -eq 'TankVisualProbe') {
        $actual = $actual.Replace("import com.mojang.blaze3d.systems.RenderSystem;`n", '')
        Assert-Contract ((Canonical-Source $actual) -ceq (Canonical-Source $expected)) 'Tank simulation, packets, floor checks or capture changed beyond the explicit native API adaptation.'
    }
    if ($name -eq 'OilRenderProbe') {
        $bucket = [regex]::Match($expected, '(?s)private static void requireBucket\(.*?(?=    private static boolean check)').Value
        Assert-Contract ($bucket.Length -gt 0 -and $actual.Contains($bucket)) 'Oil bucket identity/capability assertions changed.'
        $cases = [regex]::Match($expected, '(?s)boolean valid = true;.*?if \(!valid\).*?;').Value
        Assert-Contract ($cases.Length -gt 0 -and $actual.Contains($cases)) 'Oil/water/lava cases, sprites or failure aggregation changed.'
        Assert-Contract ($target.Contains('buffer.popNextBuffer()') -and $target.Contains('built.getFirst().vertexCount()')) 'Read the completed native draw state, not the reset builder count.'
        Assert-Contract ($target.Contains('element.getUsage() == usage && element.getIndex() == index')) 'Native vertex offsets must use the actual format elements.'
        Assert-Contract ($target.Contains('net.minecraft.tags.TagCollectionManager.bind(previousTags);') -and $target.Contains('fluidRegistry.reset(restore);') -and $target.Contains('FluidTags.getAllTags() == previousFluids')) 'Synthetic fluid tags must be restored.'
        Assert-Contract ($target.Contains('FluidTags.getAllTags();') -and $target.Contains('fluidRegistry.reset(temporary);') -and $target -notmatch '\.bindToGlobal\(') 'Worldless setup must not bind unresolved block/item wrappers to themselves.'
        Assert-Contract ($target -notmatch 'IEnviromentBlockReader|getTextureMap\(|getByteBuffer\(|getUvOffsetById\(|getColorOffset\(') 'Removed 1.14 fluid/atlas/vertex APIs remain.'
    }
}
$pack = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'resources/pack.mcmeta') -Raw | ConvertFrom-Json
Assert-Contract ($pack.pack.pack_format -eq 6) 'Native 1.16 probe resource pack must use format 6.'
$metadata = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'resources/META-INF/mods.toml') -Raw
foreach ($pin in 'loaderVersion="[36,37)"','versionRange="[1.16.5]"','versionRange="[36.2.34]"','versionRange="[2.4.7.116051]"') {
    Assert-Contract ($metadata.Contains($pin)) "Missing native probe metadata pin: $pin"
}
$runner = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'run.ps1') -Raw
$failure = [regex]::Match($runner, '(?s)\} catch \{\s*# Preserve native partial results.*?\} finally \{').Value
Assert-Contract ($failure.Contains('runner-failure.json') -and $failure.Contains('if (-not (Test-Path')) 'Runner must preserve partial native results and write its failure separately.'
Assert-Contract ($runner -notmatch '\b(?:Remove-Item|Clear-Content)\b') 'Runner must not delete old or failed evidence.'
Write-Output "PASS: $checks static probe preservation checks against $ReferenceRoot. Compilation and graphical acceptance NOT RUN."
