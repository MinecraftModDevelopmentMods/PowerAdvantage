param([string]$ReferenceRoot = (Join-Path $PSScriptRoot '../../../../PA116/PowerAdvantage/test-rig/client-resources'))
$ErrorActionPreference = 'Stop'
$ReferenceRoot = (Resolve-Path -LiteralPath $ReferenceRoot).Path
$relative = 'java/zone/moddev/mc/advantageworks/client/MachineGuiLayoutProbe.java'
$checks = 0
function Assert-Contract([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
    $script:checks++
}
function Normalize-Gui([string]$Source) {
    # Only native 1.15 API names differ. Pixel oracles, fixtures and limits do not.
    $pairs = [ordered]@{
        'import com.mojang.blaze3d.matrix.MatrixStack;' = ''
        'net.minecraft.client.resources.I18n.get(' = 'net.minecraft.client.resources.I18n.format('
        'getDeclaredField("cooldowns")' = 'getDeclaredField("cooldownTracker")'
        '.defaultBlockState()' = '.getDefaultState()'
        'getDeclaredField("isClientSide")' = 'getDeclaredField("isRemote")'
        'tile.setLevelAndPosition(' = 'tile.setWorldAndPos('
        '.createMenu(' = '.createContainer('
        'menu.slots' = 'menu.inventorySlots'
        '.getMenu()' = '.getContainer()'
        '.slots.get(' = '.inventorySlots.get('
        '.slots.size()' = '.inventorySlots.size()'
        'slot.x' = 'slot.xPos'; 'slot.y' = 'slot.yPos'
        'other.x' = 'other.xPos'; 'other.y' = 'other.yPos'
        'hotbar.x' = 'hotbar.xPos'; 'hotbar.y' = 'hotbar.yPos'
        'inventory.setItem(' = 'inventory.setInventorySlotContents('
        '.setValue(' = '.with('
        'net.minecraft.state.Property<?>' = 'net.minecraft.state.IProperty<?>'
        'Minecraft.ON_OSX' = 'Minecraft.IS_RUNNING_ON_MAC'
        '.bindWrite(' = '.bindFramebuffer('
        '.destroyBuffers()' = '.deleteFramebuffer()'
        'minecraft.getMainRenderTarget()' = 'minecraft.getFramebuffer()'
        'screen.render(new MatrixStack(), ' = 'screen.render('
        'super(null, null, net.minecraft.world.World.OVERWORLD, null, 0, () -> net.minecraft.profiler.EmptyProfiler.INSTANCE, null, false, 0);' = 'super(null, null, net.minecraft.world.dimension.DimensionType.OVERWORLD, 0, net.minecraft.profiler.EmptyProfiler.INSTANCE, null);'
        'protected void renderLabels(MatrixStack matrices, int x, int y)' = 'protected void drawGuiContainerForegroundLayer(int x, int y)'
        'super.renderLabels(matrices, x, y)' = 'super.drawGuiContainerForegroundLayer(x, y)'
        'font.width(' = 'font.getStringWidth('
        'font.drawShadow(matrices, ' = 'font.drawStringWithShadow('
        'public void render(MatrixStack matrices, int x, int y, float ticks)' = 'public void render(int x, int y, float ticks)'
        'super.render(matrices, x, y, ticks)' = 'super.render(x, y, ticks)'
        'protected void renderTooltip(MatrixStack matrices, int x, int y)' = 'protected void renderHoveredToolTip(int x, int y)'
        'super.renderTooltip(matrices, x, y)' = 'super.renderHoveredToolTip(x, y)'
        'public void renderTooltip(MatrixStack matrices, net.minecraft.item.ItemStack item, int x, int y)' = 'public void renderTooltip(net.minecraft.item.ItemStack item, int x, int y)'
        'super.renderTooltip(matrices, item, x, y)' = 'super.renderTooltip(item, x, y)'
        'public void renderTooltip(MatrixStack matrices, net.minecraft.util.text.ITextComponent text, int x, int y)' = 'public void renderTooltip(String text, int x, int y)'
        'titleTooltip = text.getString();' = 'titleTooltip = text;'
        'super.renderTooltip(matrices, text, x, y)' = 'super.renderTooltip(text, x, y)'
    }
    $Source = $Source.Replace("`r`n", "`n")
    foreach ($pair in $pairs.GetEnumerator()) { $Source = $Source.Replace($pair.Key, $pair.Value) }
    $Source = $Source.Replace('xPosPos', 'xPos').Replace('yPosPos', 'yPos')
    # The red fixture must compile before the production title resolver exists.
    # Its fallback reproduces the old provider, never the expected label list.
    $Source = [regex]::Replace($Source, '(?s)Method titleFactory = MachineGUIRegistry.class.getDeclaredMethod\("menuTitle", TileEntity.class, Block.class\);\s*titleFactory.setAccessible\(true\);\s*net.minecraft.util.text.ITextComponent title = \(net.minecraft.util.text.ITextComponent\) titleFactory.invoke\(null, tile, block\);', 'net.minecraft.util.text.ITextComponent title = menuTitle(tile, block);')
    $Source = [regex]::Replace($Source, '(?s)    private static net.minecraft.util.text.ITextComponent menuTitle\(.*?(?=    private static void verifySlots\()', '')
    $Source = [regex]::Replace($Source, '(?m)^import [^\r\n]+\r?\n', '')
    $Source = [regex]::Replace($Source, '/\*[\s\S]*?\*/|//[^\r\n]*', '')
    [regex]::Replace($Source, '\s+', '')
}
$reference = Join-Path $ReferenceRoot $relative
Assert-Contract ((Get-FileHash -LiteralPath $reference -Algorithm SHA256).Hash -eq '8D89215AEFFDDC96A7175AE84F315DFF009D6E0F0FC1942C986E626B5806AFFF') 'GUI reference changed; do not silently substitute a different accepted oracle.'
$expected = Normalize-Gui (Get-Content -LiteralPath $reference -Raw)
$actual = Normalize-Gui (Get-Content -LiteralPath (Join-Path $PSScriptRoot $relative) -Raw)
Assert-Contract ($actual -ceq $expected) 'GUI fixtures, caption pixels, slot coordinates, helper bounds or negative controls changed beyond the declared 1.15 API adaptation.'
$labels = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'resources/gui-label-expectations.json') -Raw | ConvertFrom-Json
$referenceLabels = Get-Content -LiteralPath (Join-Path $ReferenceRoot 'resources/gui-label-expectations.json') -Raw | ConvertFrom-Json
Assert-Contract (($labels | ConvertTo-Json -Depth 6 -Compress) -ceq ($referenceLabels | ConvertTo-Json -Depth 6 -Compress)) 'Independent equipment names or label rows changed.'
Assert-Contract (@($labels.machines.PSObject.Properties).Count -eq 39) 'Exactly 39 human-reviewed equipment names are required.'
Assert-Contract ($labels.locale -eq 'en_us' -and $labels.inventory -ceq 'Inventory') 'Required literal captions are missing.'
$probe = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'java/zone/moddev/mc/advantageworks/client/ClientResourceProbe.java') -Raw
Assert-Contract ($probe.Contains('MachineGuiLayoutProbe.verify(minecraft, result);')) 'Native client must dispatch the complete GUI probe.'
$runner = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'run.ps1') -Raw
foreach ($literal in '[switch]$MachineGuiLayouts', '-Dadvantageworks.machineGuiLayouts=true',
        '$result.machine_gui_layout_status -ne ''PASS''', '$result.machine_gui_text_spec -ne 2',
        '$result.machine_gui_text_status -ne ''PASS''', '$result.machine_gui_text_checks -ne $expectedGuiTextChecks',
        '$result.machine_gui_negative_controls_rejected -ne ($expectedGuiTextChecks + 4)',
        '$expectedGuiTextChecks = if ($RequireExtensions) { 351 } else { 126 }') {
    Assert-Contract ($runner.Contains($literal)) "Native runner lost a required GUI acceptance gate: $literal"
}
Assert-Contract ($runner.Contains('runtime-archives') -and $runner.Contains('Dependency changed during snapshot:')) 'Combined client must snapshot mutable development dependencies.'
Write-Output "PASS: $checks GUI oracle preservation and native runner gates. Native caption/layout rendering NOT RUN."
