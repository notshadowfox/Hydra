package hgloot.plugin;

import static org.mockito.Mockito.*;

import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.ServerPlayer;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.LocationalSoundPacketEvent;
import java.lang.reflect.Method;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

final class ProxyChatSettingTest {
    @Test
    void optOutCancelsOutgoingMicrophoneAndIncomingVoiceThenReenablesBoth() throws Exception {
        HGLootPlugin plugin = mock(HGLootPlugin.class);
        when(plugin.getConfig()).thenReturn(new YamlConfiguration());
        PlayerSettingsService settings = new PlayerSettingsService(plugin);
        when(plugin.playerSettingsService()).thenReturn(settings);
        HydraVoiceChatService voice = new HydraVoiceChatService(plugin);
        ServerPlayer speaker = mock(ServerPlayer.class);
        ServerPlayer receiver = mock(ServerPlayer.class);
        java.util.UUID speakerId = java.util.UUID.randomUUID();
        java.util.UUID receiverId = java.util.UUID.randomUUID();
        when(speaker.getUuid()).thenReturn(speakerId);
        when(receiver.getUuid()).thenReturn(receiverId);
        VoicechatConnection speakerConnection = mock(VoicechatConnection.class);
        VoicechatConnection receiverConnection = mock(VoicechatConnection.class);
        when(speakerConnection.getPlayer()).thenReturn(speaker);
        when(receiverConnection.getPlayer()).thenReturn(receiver);

        MicrophonePacketEvent mic = mock(MicrophonePacketEvent.class);
        when(mic.getSenderConnection()).thenReturn(speakerConnection);
        Method microphone = HydraVoiceChatService.class.getDeclaredMethod("onMicrophonePacket", MicrophonePacketEvent.class);
        microphone.setAccessible(true);
        microphone.invoke(voice, mic);
        verify(mic, never()).cancel();

        plugin.getConfig().set(PlayerSettingsService.path(speakerId, PlayerSettingsService.Toggle.PROXY_CHAT), false);
        settings.loadVoiceSnapshot(speakerId);
        microphone.invoke(voice, mic);
        verify(mic).cancel();

        LocationalSoundPacketEvent packet = mock(LocationalSoundPacketEvent.class);
        when(packet.getSenderConnection()).thenReturn(speakerConnection);
        when(packet.getReceiverConnection()).thenReturn(receiverConnection);
        Method sound = HydraVoiceChatService.class.getDeclaredMethod("onSoundPacket",
            de.maxhenkel.voicechat.api.events.SoundPacketEvent.class);
        sound.setAccessible(true);
        sound.invoke(voice, packet);
        verify(packet).cancel();

        plugin.getConfig().set(PlayerSettingsService.path(speakerId, PlayerSettingsService.Toggle.PROXY_CHAT), true);
        plugin.getConfig().set(PlayerSettingsService.path(receiverId, PlayerSettingsService.Toggle.PROXY_CHAT), false);
        settings.loadVoiceSnapshot(speakerId);
        settings.loadVoiceSnapshot(receiverId);
        sound.invoke(voice, packet);
        verify(packet, times(2)).cancel();

        plugin.getConfig().set(PlayerSettingsService.path(receiverId, PlayerSettingsService.Toggle.PROXY_CHAT), true);
        settings.loadVoiceSnapshot(receiverId);
        microphone.invoke(voice, mic);
        sound.invoke(voice, packet);
        verify(mic, times(1)).cancel();
        verify(packet, times(2)).cancel();
    }
}
