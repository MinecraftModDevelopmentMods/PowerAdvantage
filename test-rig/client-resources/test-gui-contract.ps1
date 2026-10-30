param([string]$ReferenceRoot = (Join-Path $PSScriptRoot '../../../../PA114/PowerAdvantage/test-rig/client-resources'))
$ErrorActionPreference = 'Stop'
$ReferenceRoot = (Resolve-Path -LiteralPath $ReferenceRoot).Path
$relative = 'java/zone/moddev/mc/advantageworks/client/MachineGuiLayoutProbe.java'
$checks = 0
function Assert-Contract([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
    $script:checks++
}
function Convert-Gui([string]$Source) {
    # Only native API names and constructor setup differ. Every oracle remains.
    $pairs = @'
[
  [
    "import com.mojang.blaze3d.platform.GlStateManager;",
    "import net.minecraft.client.renderer.GlStateManager;\nimport org.lwjgl.opengl.GL11;"
  ],
  [
    "net.minecraft.client.entity.player.ClientPlayerEntity",
    "net.minecraft.client.entity.EntityPlayerSP"
  ],
  [
    "ClientPlayerEntity",
    "EntityPlayerSP"
  ],
  [
    "net.minecraft.client.world.ClientWorld",
    "net.minecraft.client.multiplayer.WorldClient"
  ],
  [
    "ClientWorld",
    "WorldClient"
  ],
  [
    "PlayerEntity",
    "EntityPlayer"
  ],
  [
    "PlayerInventory",
    "InventoryPlayer"
  ],
  [
    "net.minecraft.inventory.container.Slot",
    "net.minecraft.inventory.Slot"
  ],
  [
    "TranslationTextComponent",
    "TextComponentTranslation"
  ],
  [
    "StringTextComponent",
    "TextComponentString"
  ],
  [
    "net.minecraft.block.BlockState",
    "net.minecraft.block.state.IBlockState"
  ],
  [
    "net.minecraft.fluid.Fluids",
    "net.minecraft.init.Fluids"
  ],
  [
    "net.minecraft.item.Items",
    "net.minecraft.init.Items"
  ],
  [
    "BlockPos.ZERO",
    "BlockPos.ORIGIN"
  ],
  [
    "getDeclaredField(\"DEFINITIONS\")",
    "getDeclaredField(\"guiTable\")"
  ],
  [
    ".init(minecraft,",
    ".setWorldAndResolution(minecraft,"
  ],
  [
    "screen.getContainer()",
    "screen.inventorySlots"
  ],
  [
    "new Framebuffer(width, height, true, Minecraft.IS_RUNNING_ON_MAC)",
    "new Framebuffer(width, height, true)"
  ],
  [
    "GlStateManager.clear(16384 | 256, Minecraft.IS_RUNNING_ON_MAC)",
    "GlStateManager.clear(16384 | 256)"
  ],
  [
    "GlStateManager.readPixels(0, 0, width, height, 6408, 5121, pixels)",
    "GL11.glReadPixels(0, 0, width, height, 6408, 5121, pixels)"
  ],
  [
    "super(null, null, net.minecraft.world.dimension.DimensionType.OVERWORLD, 0, net.minecraft.profiler.EmptyProfiler.INSTANCE, null);",
    "super(null, null, net.minecraft.world.dimension.DimensionType.OVERWORLD, net.minecraft.world.EnumDifficulty.NORMAL, new net.minecraft.profiler.Profiler());"
  ],
  [
    "font.",
    "fontRenderer."
  ],
  [
    "public void renderTooltip(net.minecraft.item.ItemStack item, int x, int y)",
    "public void renderToolTip(net.minecraft.item.ItemStack item, int x, int y)"
  ],
  [
    "super.renderTooltip(item, x, y)",
    "super.renderToolTip(item, x, y)"
  ],
  [
    "public void renderTooltip(String text, int x, int y)",
    "public void drawHoveringText(String text, int x, int y)"
  ],
  [
    "super.renderTooltip(text, x, y)",
    "super.drawHoveringText(text, x, y)"
  ],
  [
    "public void blit(int x, int y, int u, int v, int width, int height)",
    "public void drawTexturedModalRect(int x, int y, int u, int v, int width, int height)"
  ],
  [
    "super.blit(x, y, u, v, width, height)",
    "super.drawTexturedModalRect(x, y, u, v, width, height)"
  ],
  [
    "((SimpleMachineGUI) definition).createContainer(1, inventory, (IInventory) tile)",
    "((SimpleMachineGUI) definition).new Container(inventory, (IInventory) tile)"
  ],
  [
    "ITextComponent title, String expectedTitle, String expectedInventory) {",
    "ITextComponent title, String expectedTitle, String expectedInventory) throws Exception {"
  ],
  [
    "definition.super(menu, inventory, title);",
    "definition.super(inventory, inventoryOf(menu));\n            this.inventorySlots = menu;"
  ]
]
'@ | ConvertFrom-Json
    $Source = $Source.Replace("`r`n", "`n")
    foreach ($pair in $pairs) { $Source = $Source.Replace($pair[0], $pair[1]) }
    $Source = $Source.Replace('    private static net.minecraft.util.text.ITextComponent menuTitle(', '    private static IInventory inventoryOf(SimpleMachineGUI.Container menu) throws Exception {
        Field entity = SimpleMachineGUI.Container.class.getDeclaredField("entity");
        entity.setAccessible(true);
        return (IInventory) entity.get(menu);
    }

' + '    private static net.minecraft.util.text.ITextComponent menuTitle(')
    # 1.13 has no named-screen constructor. The production constructor receives
    # the same real inventory; long names belong to its tile, not an oracle label.
    $Source = $Source.Replace('        RecordingScreen screen = new RecordingScreen(definition, menu, inventory,
                new net.minecraft.util.text.TextComponentString(name), name, "Inventory");', '        cyano.poweradvantage.api.simple.TileEntitySimpleFluidMachine named =
                (cyano.poweradvantage.api.simple.TileEntitySimpleFluidMachine) inventoryOf(menu);
        net.minecraft.util.text.ITextComponent previousName = named.getCustomName();
        named.setCustomInventoryName(name);
        try {
        RecordingScreen screen = new RecordingScreen(definition, menu, inventory,
                new net.minecraft.util.text.TextComponentString(name), name, "Inventory");')
    $Source.Replace('        ImageIO.write(hover, "png", new File("machine-gui-layouts/metal-tank-long-name-tooltip.png"));
    }', '        ImageIO.write(hover, "png", new File("machine-gui-layouts/metal-tank-long-name-tooltip.png"));
        } finally {
            named.setCustomInventoryName(previousName == null ? null : previousName.getString());
        }
    }')
}
function Canonical-Source([string]$Source) {
    $Source = $Source.Replace("`r`n", "`n")
    $Source = [regex]::Replace($Source, '(?m)^import [^\r\n]+\r?\n', '')
    $Source = [regex]::Replace($Source, '/\*[\s\S]*?\*/|//[^\r\n]*', '')
    [regex]::Replace($Source, '\s+', '')
}
$reference = Join-Path $ReferenceRoot $relative
Assert-Contract ((Get-FileHash -LiteralPath $reference -Algorithm SHA256).Hash -eq 'E669E62922A7F5008498069E778808BEA28BDCB1E0FB933FD15845A5D9AE810A') 'GUI reference changed; do not substitute another accepted oracle.'
$expected = Canonical-Source (Convert-Gui (Get-Content -LiteralPath $reference -Raw))
$actual = Canonical-Source (Get-Content -LiteralPath (Join-Path $PSScriptRoot $relative) -Raw)
Assert-Contract ($actual -ceq $expected) 'GUI fixtures, captions, pixels, slot coordinates, helper bounds or controls changed beyond the declared 1.13 native adaptation.'
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
