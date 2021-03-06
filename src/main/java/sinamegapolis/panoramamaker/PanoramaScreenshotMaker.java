/**
 * This class was created by <SinaMegapolis>. It's distributed as
 * part of the PanoramaMaker Mod. Get the Source Code in github:
 * https://github.com/SinaMegapolis/PanoramaMaker
 *
 * PanoramaMaker is Open Source and distributed under the
 * CC-BY-NC-SA 3.0 License: https://creativecommons.org/licenses/by-nc-sa/3.0/deed.en_GB
 *
 */
package sinamegapolis.panoramamaker;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Random;

import javax.imageio.ImageIO;

import com.google.common.collect.ImmutableSet;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.text.*;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.ScreenshotEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import net.minecraftforge.fml.common.gameevent.TickEvent.RenderTickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import sinamegapolis.panoramamaker.PanoramaMaker;

public class PanoramaScreenshotMaker {
    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss");

    File panoramaDir;
    File currentDir;
    float rotationYaw, rotationPitch;
    int panoramaStep;
    boolean takingPanorama;
    int currentWidth, currentHeight;
    boolean overridenOnce;

    boolean overrideMainMenu = PanoramaMakerConfig.overrideMainMenu;
    int panoramaSize = PanoramaMakerConfig.panoramaSize;
    boolean fullscreen = PanoramaMakerConfig.fullScreen;

    @SubscribeEvent
    public void loadMainMenu(GuiOpenEvent event) {
        if(overrideMainMenu && !overridenOnce && event.getGui() instanceof GuiMainMenu) {
            File mcDir = PanoramaMaker.configFile.getParentFile().getParentFile();
            File panoramasDir = new File(mcDir, "/screenshots/panoramas");

            List<File[]> validFiles = new ArrayList();

            ImmutableSet<String> set = ImmutableSet.of("panorama_0.png", "panorama_1.png", "panorama_2.png", "panorama_3.png", "panorama_4.png", "panorama_5.png");

            if(panoramasDir.exists()) {
                File[] subDirs;

                File mainMenu = new File(panoramasDir, "main_menu");
                if(mainMenu.exists())
                    subDirs = new File[] { mainMenu };
                else subDirs = panoramasDir.listFiles((File f) -> f.isDirectory() && !f.getName().endsWith("fullres"));

                for(File f : subDirs)
                    if(set.stream().allMatch((String s) -> new File(f, s).exists()))
                        validFiles.add(f.listFiles((File f1) -> set.contains(f1.getName())));
            }

            if(!validFiles.isEmpty()) {
                File[] files = validFiles.get(new Random().nextInt(validFiles.size()));
                Arrays.sort(files);

                Minecraft mc = Minecraft.getMinecraft();
                ResourceLocation[] resources = new ResourceLocation[6];

                for(int i = 0; i < resources.length; i++) {
                    File f = files[i];
                    try {
                        BufferedImage img = ImageIO.read(f);
                        DynamicTexture tex = new DynamicTexture(img);
                        String name = "panoramamaker:" + f.getName();

                        resources[i] = mc.getTextureManager().getDynamicTextureLocation(name, tex);
                    } catch (IOException e) {
                        e.printStackTrace();
                        return;
                    }
                }

                try {
                    Field field = ReflectionHelper.findField(GuiMainMenu.class, "H", "field_73978_o", "TITLE_PANORAMA_PATHS" );
                    field.setAccessible(true);

                    if(Modifier.isFinal(field.getModifiers())) {
                        Field modfield = Field.class.getDeclaredField("modifiers");
                        modfield.setAccessible(true);
                        modfield.setInt(field, field.getModifiers() & ~Modifier.FINAL);
                    }

                    field.set(null, resources);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            overridenOnce = true;
        }
    }

    @SubscribeEvent
    public void takeScreenshot(ScreenshotEvent event) {
        if(takingPanorama)
            return;

        if(GuiScreen.isCtrlKeyDown() && GuiScreen.isShiftKeyDown() && Minecraft.getMinecraft().currentScreen == null) {
            takingPanorama = true;
            panoramaStep = 0;

            if(panoramaDir == null)
                panoramaDir = new File(event.getScreenshotFile().getParentFile(), "panoramas");
            if(!panoramaDir.exists())
                panoramaDir.mkdirs();

            int i = 0;
            String ts = getTimestamp();
            do {
                if(fullscreen) {
                    if(i == 0)
                        currentDir = new File(panoramaDir + "_fullres", ts);
                    else currentDir = new File(panoramaDir, ts + "_" + i + "_fullres");
                } else {
                    if(i == 0)
                        currentDir = new File(panoramaDir, ts);
                    else currentDir = new File(panoramaDir, ts + "_" + i);
                }
            } while(currentDir.exists());

            currentDir.mkdirs();

            event.setCanceled(true);

            ITextComponent panoramaDirComponent = new TextComponentString(currentDir.getName());
            panoramaDirComponent.getStyle().setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, currentDir.getAbsolutePath())).setUnderlined(true);
            event.setResultMessage(new TextComponentTranslation("panoramamaker.panoramaSaved", panoramaDirComponent));
        }
    }

    @SubscribeEvent
    public void renderTick(RenderTickEvent event) {
        Minecraft mc = Minecraft.getMinecraft();

        if(takingPanorama) {
            if(event.phase == Phase.START) {
                if(panoramaStep == 0) {
                    mc.gameSettings.hideGUI = true;
                    currentWidth = mc.displayWidth;
                    currentHeight = mc.displayHeight;
                    rotationYaw = mc.player.rotationYaw;
                    rotationPitch = mc.player.rotationPitch;

                    if(!fullscreen)
                        mc.resize(panoramaSize, panoramaSize);
                }

                switch(panoramaStep) {
                    case 1:
                        mc.player.rotationYaw = 180;
                        mc.player.rotationPitch = 0;
                        break;
                    case 2:
                        mc.player.rotationYaw = -90;
                        mc.player.rotationPitch = 0;
                        break;
                    case 3:
                        mc.player.rotationYaw = 0;
                        mc.player.rotationPitch = 0;
                        break;
                    case 4:
                        mc.player.rotationYaw = 90;
                        mc.player.rotationPitch = 0;
                        break;
                    case 5:
                        mc.player.rotationYaw = 180;
                        mc.player.rotationPitch = -90;
                        break;
                    case 6:
                        mc.player.rotationYaw = 180;
                        mc.player.rotationPitch = 90;
                        break;
                }
                mc.player.prevRotationYaw = mc.player.rotationYaw;
                mc.player.prevRotationPitch = mc.player.rotationPitch;
            } else {
                if(panoramaStep > 0)
                    saveScreenshot(currentDir, "panorama_" + (panoramaStep - 1) + ".png", mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
                panoramaStep++;
                if(panoramaStep == 7) {
                    mc.gameSettings.hideGUI = false;
                    takingPanorama = false;

                    mc.player.rotationYaw = rotationYaw;
                    mc.player.rotationPitch = rotationPitch;
                    mc.player.prevRotationYaw = mc.player.rotationYaw;
                    mc.player.prevRotationPitch = mc.player.rotationPitch;

                    mc.resize(currentWidth, currentHeight);
                }
            }
        }
    }

    private static void saveScreenshot(File dir, String screenshotName, int width, int height, Framebuffer buffer) {
        try {
            BufferedImage bufferedimage = ScreenShotHelper.createScreenshot(width, height, buffer);
            File file2 = new File(dir, screenshotName);

            net.minecraftforge.client.ForgeHooksClient.onScreenshot(bufferedimage, file2);
            ImageIO.write(bufferedimage, "png", file2);
        } catch(Exception exception) { }
    }

    private static String getTimestamp() {
        String s = DATE_FORMAT.format(new Date()).toString();
        return s;
    }

    @SubscribeEvent
    public void onEnteringWorld(EntityJoinWorldEvent event){
        if(event.getEntity()instanceof EntityPlayer && event.getWorld().isRemote){
            ((EntityPlayer) event.getEntity()).sendStatusMessage(new TextComponentTranslation("panoramamaker.enterWorld"), false);
        }
    }
}
