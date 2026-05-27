package it.dhd.oxygencustomizer.xposed.hooks.systemui;

import static de.robv.android.xposed.XposedBridge.hookAllMethods;
import static it.dhd.oxygencustomizer.utils.Constants.Packages.SYSTEM_UI;

import android.content.Context;
import android.hardware.camera2.CameraManager;
import android.os.Build;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.dhd.oxygencustomizer.xposed.XposedMods;

/*
 * Fixes a bootloop on Android 16 (API 36) where SystemUI crashes repeatedly on startup
 * because CameraManager.registerAvailabilityCallback throws IllegalStateException:
 * "Failed to register a camera service listener" / "Listener already registered".
 *
 * Root cause: the camera service retains the binder from the previous SystemUI process
 * after it crashes, so when SystemUI restarts and tries to register a new camera
 * availability callback (via FacePropertyRepositoryImpl.cameraInfo for face-unlock),
 * the camera service rejects the registration. The uncaught exception propagates through
 * SystemUIApplication, triggers repeated crashes, and eventually trips Android's
 * persistent-app reboot threshold — causing a device bootloop.
 *
 * Fix: suppress the exception so SystemUI boots cleanly. The face-unlock camera
 * availability callback may be inactive until the next clean boot; all other camera
 * functionality is unaffected.
 */
public class FaceAuthCameraFix extends XposedMods {

    public FaceAuthCameraFix(Context context) {
        super(context);
    }

    @Override
    public void updatePrefs(String... Key) {}

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (Build.VERSION.SDK_INT < 36) return;

        try {
            hookAllMethods(CameraManager.class, "registerAvailabilityCallback", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Throwable t = param.getThrowable();
                    if (!(t instanceof IllegalStateException)) return;
                    String msg = t.getMessage();
                    if (msg != null && msg.contains("Failed to register a camera service listener")) {
                        param.setThrowable(null);
                    }
                }
            });
        } catch (Throwable t) {
            log(t);
        }
    }

    @Override
    public boolean listensTo(String packageName) {
        return SYSTEM_UI.equals(packageName);
    }
}
