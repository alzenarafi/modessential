package id.skinshot;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public class SkinShotClient implements ClientModInitializer {

	public static final String MOD_ID = "skinshot";

	private static KeyMapping openKey;

	@Override
	public void onInitializeClient() {
		KeyMapping.Category category = KeyMapping.Category.register(
				Identifier.fromNamespaceAndPath(MOD_ID, "main"));

		openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.skinshot.open",
				InputConstants.Type.KEYBOARD,
				InputConstants.KEY_K,
				category));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openKey.consumeClick()) {
				client.gui.setScreen(new ScreenshotScreen(client.gui.screen()));
			}
		});
	}
}
