package com.example.immersiveciv.screen;

import com.example.immersiveciv.network.ModMessages;
import com.example.immersiveciv.registry.BuildingRecord;
import com.example.immersiveciv.registry.ClientBuildingRegistry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Экран «Городская карта».
 *
 * Левая часть — карта с поддержкой:
 *   • Перетаскивание (ЛКМ + drag) → пан
 *   • Колесо мыши → зум (по позиции курсора)
 *   • Клавиша R или кнопка «Fit» → сброс к виду «все здания»
 *
 * Правая часть — панель информации о выбранном/наведённом здании.
 */
public class CityMapScreen extends Screen {

    // ── Размеры GUI ───────────────────────────────────────────────────────────
    private static final int GUI_W   = 340;
    private static final int GUI_H   = 210;
    private static final int MAP_W   = 210;
    private static final int MAP_H   = 185;
    private static final int MAP_L   = 8;   // отступ карты слева
    private static final int MAP_T   = 16;  // отступ карты сверху (место для заголовка)
    private static final int INFO_W  = 110; // ширина правой панели
    private static final int INFO_GAP = 4;  // зазор между картой и панелью

    // ── Зум ───────────────────────────────────────────────────────────────────
    private static final double MIN_ZOOM =  0.02;  // 1 блок = 0.02 пкс (огромные расстояния)
    private static final double MAX_ZOOM = 20.0;   // 1 блок = 20 пкс (детальный осмотр)
    private static final double ZOOM_STEP = 1.15;  // множитель за один тик колеса

    // ── Цвета (ARGB) ──────────────────────────────────────────────────────────
    private static final int COL_BG         = 0xFF1A1A2E;
    private static final int COL_MAP_BG     = 0xFF0D0D1A;
    private static final int COL_MAP_BORDER = 0xFF3A3A5A;
    private static final int COL_INFO_BG    = 0xFF16213E;
    private static final int COL_SELECTED   = 0xFFFFD700;
    private static final int COL_DIM_TEXT   = 0xFF666688;
    private static final int COL_GRID       = 0x15FFFFFF;  // очень тусклая сетка

    // ── Цвета зданий по типу ──────────────────────────────────────────────────
    private static final Map<String, Integer> TYPE_COLORS = Map.ofEntries(
            Map.entry("forge",       0xFFE07030),
            Map.entry("foundry",     0xFFD06020),
            Map.entry("assembly",    0xFFB05020),
            Map.entry("press",       0xFFCC6040),
            Map.entry("depot",       0xFF806040),
            Map.entry("farm",        0xFF50B040),
            Map.entry("barn",        0xFF80A030),
            Map.entry("greenhouse",  0xFF30C060),
            Map.entry("windmill",    0xFF70C070),
            Map.entry("bakery",      0xFFD0A040),
            Map.entry("tavern",      0xFFC08030),
            Map.entry("barracks",    0xFFB03030),
            Map.entry("watchtower",  0xFF903030),
            Map.entry("armory",      0xFF802020),
            Map.entry("outpost",     0xFFAA5050),
            Map.entry("library",     0xFF3060C0),
            Map.entry("alchemylab",  0xFF6040B0),
            Map.entry("mage_tower",  0xFF8020D0),
            Map.entry("observatory", 0xFF2050D0),
            Map.entry("archive",     0xFF4070A0),
            Map.entry("town_hall",   0xFF8030C0),
            Map.entry("bank",        0xFF9040B0),
            Map.entry("market",      0xFFA050A0),
            Map.entry("free",        0xFF505080)
    );

    // ── Состояние камеры ──────────────────────────────────────────────────────
    /** Мировые координаты центра видимой области карты. */
    private double cameraX = 0.0;
    private double cameraZ = 0.0;
    /** Пикселей на один блок. */
    private double zoom = 1.0;

    // ── Состояние перетаскивания ──────────────────────────────────────────────
    private boolean isDragging = false;
    private boolean dragMoved  = false;
    private double  dragStartMouseX, dragStartMouseY;
    private double  dragStartCameraX, dragStartCameraZ;
    private static final int DRAG_THRESHOLD = 3; // px до начала пана

    // ── Фиксированные границы области карты (пиксели) ─────────────────────────
    // Устанавливаются в init(), не меняются при панировании/зуме.
    private int mapAreaL, mapAreaT, mapAreaR, mapAreaB;
    private int mapCX, mapCY; // центр области карты

    // ── Выделение ─────────────────────────────────────────────────────────────
    private BuildingRecord hoveredBuilding  = null;
    private BuildingRecord selectedBuilding = null;

    // ── Виджеты ───────────────────────────────────────────────────────────────
    private Button demolishButton;
    private Button fitButton;

    // ── Конструктор / init ────────────────────────────────────────────────────

    public CityMapScreen() {
        super(Component.literal("§6Городская карта"));
    }

    @Override
    protected void init() {
        int guiLeft = (this.width  - GUI_W) / 2;
        int guiTop  = (this.height - GUI_H) / 2;

        // Фиксируем границы карты раз и навсегда
        mapAreaL = guiLeft + MAP_L;
        mapAreaT = guiTop  + MAP_T;
        mapAreaR = mapAreaL + MAP_W;
        mapAreaB = mapAreaT + MAP_H;
        mapCX    = (mapAreaL + mapAreaR) / 2;
        mapCY    = (mapAreaT + mapAreaB) / 2;

        // Кнопка «Вписать» — в правом верхнем углу карты
        fitButton = Button.builder(
                Component.literal("⌖"),
                btn -> resetView()
        ).pos(mapAreaR - 14, mapAreaT + 2).size(12, 10).build();
        this.addRenderableWidget(fitButton);

        // Кнопка «Снести» — в нижней части панели информации
        int infoPanelL = mapAreaR + INFO_GAP;
        demolishButton = Button.builder(
                Component.literal("§cСнести"),
                btn -> demolishSelected()
        ).pos(infoPanelL + 2, mapAreaB - 16).size(INFO_W - 4, 14).build();
        demolishButton.visible = false;
        this.addRenderableWidget(demolishButton);

        // Устанавливаем начальный вид только при первом открытии (zoom == 1.0 = default)
        if (zoom == 1.0) resetView();
    }

    // ── Камера ────────────────────────────────────────────────────────────────

    /**
     * Устанавливает камеру так, чтобы вместить все здания с отступом.
     * Вызывается при открытии экрана и по нажатию кнопки «⌖» / R.
     */
    private void resetView() {
        Collection<BuildingRecord> all = ClientBuildingRegistry.getAll();
        if (all.isEmpty()) {
            cameraX = 0; cameraZ = 0; zoom = 2.0;
            return;
        }

        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (BuildingRecord r : all) {
            minX = Math.min(minX, Math.min(r.pos1x, r.pos2x));
            maxX = Math.max(maxX, Math.max(r.pos1x, r.pos2x));
            minZ = Math.min(minZ, Math.min(r.pos1z, r.pos2z));
            maxZ = Math.max(maxZ, Math.max(r.pos1z, r.pos2z));
        }

        // 15% отступ + минимум 16 блоков, чтобы одиночное здание не растянулось на весь экран
        double padX = Math.max((maxX - minX) * 0.15, 16);
        double padZ = Math.max((maxZ - minZ) * 0.15, 16);
        double worldW = (maxX - minX) + padX * 2;
        double worldH = (maxZ - minZ) + padZ * 2;

        zoom = Math.min(MAP_W / worldW, MAP_H / worldH);
        zoom = clampZoom(zoom);

        cameraX = (minX + maxX) / 2.0;
        cameraZ = (minZ + maxZ) / 2.0;
    }

    private double clampZoom(double z) {
        return Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, z));
    }

    // ── Координатные преобразования ───────────────────────────────────────────

    private int worldToScreenX(double wx) {
        return (int)(mapCX + (wx - cameraX) * zoom);
    }

    private int worldToScreenZ(double wz) {
        return (int)(mapCY + (wz - cameraZ) * zoom);
    }

    private double screenToWorldX(double sx) {
        return cameraX + (sx - mapCX) / zoom;
    }

    private double screenToWorldZ(double sz) {
        return cameraZ + (sz - mapCY) / zoom;
    }

    private boolean isInMapArea(double mx, double my) {
        return mx >= mapAreaL && mx < mapAreaR && my >= mapAreaT && my < mapAreaB;
    }

    // ── Ввод: пан ─────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && isInMapArea(mx, my)) {
            isDragging      = true;
            dragMoved       = false;
            dragStartMouseX  = mx;
            dragStartMouseY  = my;
            dragStartCameraX = cameraX;
            dragStartCameraZ = cameraZ;
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (isDragging && button == 0) {
            double totalDx = mx - dragStartMouseX;
            double totalDy = my - dragStartMouseY;
            if (!dragMoved && (Math.abs(totalDx) > DRAG_THRESHOLD || Math.abs(totalDy) > DRAG_THRESHOLD)) {
                dragMoved = true;
            }
            if (dragMoved) {
                cameraX = dragStartCameraX - totalDx / zoom;
                cameraZ = dragStartCameraZ - totalDy / zoom;
                return true;
            }
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (isDragging && button == 0) {
            isDragging = false;
            if (!dragMoved) {
                // Клик без движения → выбор здания
                if (hoveredBuilding != null) {
                    boolean same = selectedBuilding != null
                            && hoveredBuilding.id.equals(selectedBuilding.id);
                    selectedBuilding      = same ? null : hoveredBuilding;
                    demolishButton.visible = (selectedBuilding != null);
                } else {
                    selectedBuilding      = null;
                    demolishButton.visible = false;
                }
            }
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    // ── Ввод: зум ─────────────────────────────────────────────────────────────

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (!isInMapArea(mx, my)) return super.mouseScrolled(mx, my, delta);

        double oldZoom = zoom;
        zoom = clampZoom(zoom * (delta > 0 ? ZOOM_STEP : 1.0 / ZOOM_STEP));

        if (zoom != oldZoom) {
            // Зумируем к позиции курсора: до и после зума курсор должен
            // указывать на ту же мировую точку.
            double wx = cameraX + (mx - mapCX) / oldZoom;
            double wz = cameraZ + (my - mapCY) / oldZoom;
            cameraX = wx - (mx - mapCX) / zoom;
            cameraZ = wz - (my - mapCY) / zoom;
        }
        return true;
    }

    // ── Ввод: клавиши ─────────────────────────────────────────────────────────

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // R или H — сброс вида
        if (keyCode == 82 || keyCode == 72) { resetView(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        renderBackground(g);
        renderGui(g, mouseX, mouseY);
        super.render(g, mouseX, mouseY, delta); // кнопки
    }

    private void renderGui(GuiGraphics g, int mouseX, int mouseY) {
        int guiL = mapAreaL - MAP_L;
        int guiT = mapAreaT - MAP_T;

        // ── Фон GUI ──────────────────────────────────────────────────────────
        g.fill(guiL, guiT, guiL + GUI_W, guiT + GUI_H, COL_BG);

        // ── Заголовок ────────────────────────────────────────────────────────
        g.drawCenteredString(font, "§6Городская карта",
                guiL + (MAP_L + MAP_W) / 2, guiT + 4, 0xFFFFAA00);

        // ── Фон и рамка карты ────────────────────────────────────────────────
        g.fill(mapAreaL, mapAreaT, mapAreaR, mapAreaB, COL_MAP_BG);
        drawBorder(g, mapAreaL, mapAreaT, mapAreaR, mapAreaB, COL_MAP_BORDER);

        // ── Сетка (только при достаточном зуме) ─────────────────────────────
        if (zoom >= 1.0) renderGrid(g);

        // ── Здания с scissor-клипом ───────────────────────────────────────────
        g.enableScissor(mapAreaL + 1, mapAreaT + 1, mapAreaR - 1, mapAreaB - 1);
        hoveredBuilding = null;
        Collection<BuildingRecord> all = ClientBuildingRegistry.getAll();
        if (all.isEmpty()) {
            g.disableScissor();
            g.drawCenteredString(font, "§7Нет зарегистрированных зданий",
                    mapCX, mapCY - 4, COL_DIM_TEXT);
        } else {
            for (BuildingRecord r : all) renderBuilding(g, r, mouseX, mouseY);
            g.disableScissor();
        }

        // ── Подсказка управления + координаты ────────────────────────────────
        renderMapOverlay(g, mouseX, mouseY);

        // ── Панель информации ─────────────────────────────────────────────────
        renderInfoPanel(g, mouseX, mouseY);
    }

    // ── Сетка ─────────────────────────────────────────────────────────────────

    /**
     * Тусклая сетка кратная 16 блокам (чанки).
     * Видна только при zoom >= 1.0, чтобы не превращаться в шум.
     */
    private void renderGrid(GuiGraphics g) {
        int gridSize = 16; // блоков
        // ширина мира, видимая на экране
        double visW = MAP_W / zoom;
        double visH = MAP_H / zoom;
        double worldLeft = screenToWorldX(mapAreaL);
        double worldTop  = screenToWorldZ(mapAreaT);

        int startX = (int)Math.floor(worldLeft / gridSize) * gridSize;
        int startZ = (int)Math.floor(worldTop  / gridSize) * gridSize;

        g.enableScissor(mapAreaL, mapAreaT, mapAreaR, mapAreaB);
        for (int wx = startX; wx <= worldLeft + visW + gridSize; wx += gridSize) {
            int sx = worldToScreenX(wx);
            g.fill(sx, mapAreaT, sx + 1, mapAreaB, COL_GRID);
        }
        for (int wz = startZ; wz <= worldTop + visH + gridSize; wz += gridSize) {
            int sz = worldToScreenZ(wz);
            g.fill(mapAreaL, sz, mapAreaR, sz + 1, COL_GRID);
        }
        g.disableScissor();
    }

    // ── Здания ────────────────────────────────────────────────────────────────

    private void renderBuilding(GuiGraphics g, BuildingRecord r, int mouseX, int mouseY) {
        int sx1 = worldToScreenX(Math.min(r.pos1x, r.pos2x));
        int sz1 = worldToScreenZ(Math.min(r.pos1z, r.pos2z));
        int sx2 = worldToScreenX(Math.max(r.pos1x, r.pos2x));
        int sz2 = worldToScreenZ(Math.max(r.pos1z, r.pos2z));

        // Минимальный размер для кликабельности
        if (sx2 - sx1 < 3) { int cx = (sx1 + sx2) / 2; sx1 = cx - 1; sx2 = cx + 2; }
        if (sz2 - sz1 < 3) { int cz = (sz1 + sz2) / 2; sz1 = cz - 1; sz2 = cz + 2; }

        // Hover проверка только внутри области карты
        int hx1 = Math.max(sx1, mapAreaL + 1);
        int hz1 = Math.max(sz1, mapAreaT + 1);
        int hx2 = Math.min(sx2, mapAreaR - 1);
        int hz2 = Math.min(sz2, mapAreaB - 1);
        boolean isHover = isInMapArea(mouseX, mouseY)
                && mouseX >= hx1 && mouseX < hx2
                && mouseY >= hz1 && mouseY < hz2;
        boolean isSel = selectedBuilding != null && r.id.equals(selectedBuilding.id);

        if (isHover) hoveredBuilding = r;

        int baseColor   = TYPE_COLORS.getOrDefault(r.type, 0xFF606060);
        int alpha       = isHover ? 0xC0 : 0x70;
        int fillColor   = (baseColor & 0x00FFFFFF) | (alpha << 24);
        int borderColor = isSel ? COL_SELECTED : baseColor;

        g.fill(sx1, sz1, sx2, sz2, fillColor);
        drawBorder(g, sx1, sz1, sx2, sz2, borderColor);
        if (isSel) drawBorder(g, sx1 + 1, sz1 + 1, sx2 - 1, sz2 - 1, borderColor); // двойная рамка

        // Метка типа
        int bw = sx2 - sx1, bh = sz2 - sz1;
        if (bw > 8 && bh > 8) {
            String label;
            if (zoom >= 3.0 && bw > 32) {
                label = prettifyType(r.type); // полное название при высоком зуме
            } else if (bw > 14) {
                label = r.type.substring(0, Math.min(3, r.type.length()));
            } else {
                label = r.type.substring(0, 1).toUpperCase();
            }
            int lx = (sx1 + sx2) / 2 - font.width(label) / 2;
            int lz = (sz1 + sz2) / 2 - font.lineHeight / 2;
            g.drawString(font, label, lx, lz, 0xFFFFFFFF, true);
        }
    }

    // ── Оверлей поверх карты ──────────────────────────────────────────────────

    private void renderMapOverlay(GuiGraphics g, int mouseX, int mouseY) {
        // Уровень зума (правый нижний угол)
        String zoomStr = String.format("×%.1f", zoom);
        g.drawString(font, "§8" + zoomStr,
                mapAreaR - font.width(zoomStr) - 3, mapAreaB - 9, COL_DIM_TEXT, false);

        // Мировые координаты курсора (левый нижний угол)
        if (isInMapArea(mouseX, mouseY)) {
            String coords = String.format("§8X%.0f Z%.0f",
                    screenToWorldX(mouseX), screenToWorldZ(mouseY));
            g.drawString(font, coords, mapAreaL + 3, mapAreaB - 9, COL_DIM_TEXT, false);
        } else {
            g.drawString(font, "§8[R] — вписать всё", mapAreaL + 3, mapAreaB - 9, COL_DIM_TEXT, false);
        }
    }

    // ── Панель информации ─────────────────────────────────────────────────────

    private void renderInfoPanel(GuiGraphics g, int mouseX, int mouseY) {
        int pL = mapAreaR + INFO_GAP;
        int pT = mapAreaT;
        int pR = pL + INFO_W;
        int pB = mapAreaB;

        g.fill(pL, pT, pR, pB, COL_INFO_BG);
        drawBorder(g, pL, pT, pR, pB, COL_MAP_BORDER);

        BuildingRecord shown = (selectedBuilding != null) ? selectedBuilding : hoveredBuilding;
        if (shown == null) {
            g.drawString(font, "§7Наведи на здание",  pL + 5, pT + 7,  0xFF888899, false);
            g.drawString(font, "§7или выбери (ЛКМ)",  pL + 5, pT + 17, 0xFF777788, false);
            g.drawString(font, "§8─────────────────",  pL + 2, pT + 29, 0xFF333350, false);
            g.drawString(font, "§8ЛКМ + drag = пан",  pL + 5, pT + 38, 0xFF555566, false);
            g.drawString(font, "§8Колесо = зум",       pL + 5, pT + 47, 0xFF555566, false);
            g.drawString(font, "§8R / ⌖ = вписать",   pL + 5, pT + 56, 0xFF555566, false);
            demolishButton.visible = false;
            return;
        }

        int ty = pT + 5;
        int typeColor = TYPE_COLORS.getOrDefault(shown.type, 0xFFAAAAAA);

        // Полосочка-цвет типа
        g.fill(pL + 2, ty - 1, pL + 6, ty + font.lineHeight - 1, typeColor);

        // Тип
        g.drawString(font, prettifyType(shown.type), pL + 9, ty, typeColor, false);
        ty += font.lineHeight + 2;

        // Владелец
        String ownerLine = "§7" + (shown.owner.length() > 14 ? shown.owner.substring(0, 12) + "…" : shown.owner);
        g.drawString(font, ownerLine, pL + 5, ty, 0xFFAABBCC, false);
        ty += font.lineHeight + 1;

        // Возраст
        long daysAgo = (System.currentTimeMillis() - shown.timestamp) / 86_400_000L;
        String age = daysAgo == 0 ? "сегодня" : daysAgo + " дн.";
        g.drawString(font, "§8" + age, pL + 5, ty, 0xFF778899, false);
        ty += font.lineHeight + 4;

        // Разделитель
        g.fill(pL + 4, ty, pR - 4, ty + 1, 0xFF2A2A4A);
        ty += 5;

        // Оценка VLM
        if (shown.vlmScore >= 0) {
            g.drawString(font, "§eОценка: " + shown.vlmScore + "/100", pL + 5, ty, 0xFFEECC00, false);
            ty += font.lineHeight + 1;

            int barW = INFO_W - 12;
            g.fill(pL + 5, ty, pL + 5 + barW, ty + 5, 0xFF222233);
            int filled = (int)(barW * shown.vlmScore / 100.0);
            int barC = shown.vlmScore >= 70 ? 0xFF44BB44
                     : shown.vlmScore >= 40 ? 0xFFEEAA22
                     :                        0xFFBB3333;
            if (filled > 0) g.fill(pL + 5, ty, pL + 5 + filled, ty + 5, barC);
            drawBorder(g, pL + 5, ty, pL + 5 + barW, ty + 5, 0xFF444466);
            ty += 10;
        }

        // Краткий отзыв
        if (!shown.vlmSummary.isEmpty()) {
            ty += 2;
            // Используем ширину в пикселях для переноса
            int maxLineW = INFO_W - 12;
            List<String> lines = wrapTextPx(shown.vlmSummary, maxLineW);
            int maxLines = (pB - 22 - ty) / (font.lineHeight + 1);
            for (int i = 0; i < Math.min(lines.size(), maxLines); i++) {
                g.drawString(font, "§7" + lines.get(i), pL + 5, ty, 0xFF9999AA, false);
                ty += font.lineHeight + 1;
            }
        }

        // Кнопка Снести
        demolishButton.visible = true;
        demolishButton.setY(pB - 18);
    }

    // ── Действия ──────────────────────────────────────────────────────────────

    private void demolishSelected() {
        if (selectedBuilding == null) return;
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeUtf(selectedBuilding.id, 64);
        ClientPlayNetworking.send(ModMessages.DEMOLISH_BUILDING, buf);
        selectedBuilding      = null;
        demolishButton.visible = false;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ── Вспомогательные ───────────────────────────────────────────────────────

    private static void drawBorder(GuiGraphics g, int x1, int y1, int x2, int y2, int c) {
        g.fill(x1,     y1,     x2,     y1 + 1, c); // верх
        g.fill(x1,     y2 - 1, x2,     y2,     c); // низ
        g.fill(x1,     y1,     x1 + 1, y2,     c); // лево
        g.fill(x2 - 1, y1,     x2,     y2,     c); // право
    }

    private static String prettifyType(String type) {
        return switch (type) {
            case "forge"       -> "Кузница";
            case "farm"        -> "Ферма";
            case "barn"        -> "Амбар";
            case "bakery"      -> "Пекарня";
            case "tavern"      -> "Таверна";
            case "greenhouse"  -> "Теплица";
            case "windmill"    -> "Мельница";
            case "foundry"     -> "Плавильня";
            case "press"       -> "Пресс";
            case "assembly"    -> "Сборочная";
            case "depot"       -> "Депо";
            case "watchtower"  -> "Башня";
            case "barracks"    -> "Казарма";
            case "armory"      -> "Оружейная";
            case "outpost"     -> "Застава";
            case "library"     -> "Библиотека";
            case "alchemylab"  -> "Лаборатория";
            case "mage_tower"  -> "Башня мага";
            case "observatory" -> "Обсерватория";
            case "archive"     -> "Архив";
            case "town_hall"   -> "Ратуша";
            case "bank"        -> "Банк";
            case "market"      -> "Рынок";
            case "free"        -> "Свободная";
            default            -> type;
        };
    }

    /**
     * Перенос текста с учётом реальной ширины в пикселях (а не по символам).
     */
    private List<String> wrapTextPx(String text, int maxWidthPx) {
        List<String> result = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            String test = line.isEmpty() ? word : line + " " + word;
            if (font.width(test) > maxWidthPx && !line.isEmpty()) {
                result.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(test);
            }
        }
        if (!line.isEmpty()) result.add(line.toString());
        return result;
    }
}
