package com.myapp;

import java.util.HashMap;
import java.util.Map;

import javax.security.auth.login.LoginException;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.managers.AudioManager;

public class DiscordBotMain extends ListenerAdapter {

	public static void main(String[] args) throws LoginException {

		JDA jda = JDABuilder.createDefault("OTE1MTAwMDc3NzUwNjQ0NzQ3.YaWrXA.pA88vd2bYFZi1ejhxovL2YSoQ3E").build();

		jda.addEventListener(new DiscordBotMain());
	}

	private final AudioPlayerManager playerManager;
	private final Map<Long, GuildMusicManager> musicManagers;

	private DiscordBotMain() {
		this.musicManagers = new HashMap<>();

		this.playerManager = new DefaultAudioPlayerManager();
		AudioSourceManagers.registerRemoteSources(playerManager);
		AudioSourceManagers.registerLocalSource(playerManager);
	}

	private synchronized GuildMusicManager getGuildAudioPlayer(Guild guild) {

		long guildId = Long.parseLong(guild.getId());
		GuildMusicManager musicManager = musicManagers.get(guildId);

		if (musicManager == null) {
			musicManager = new GuildMusicManager(playerManager);
			musicManagers.put(guildId, musicManager);
		}

		guild.getAudioManager().setSendingHandler(musicManager.getSendHandler());

		return musicManager;
	}

	@Override
	public void onMessageReceived(MessageReceivedEvent event) {

		if (event.getMessage().getContentRaw().equals("!명령어")) {
			event.getChannel().sendMessage("!재생 유튜브링크: 음악재생").queue();
		}
	}

	@Override
	public void onGuildMessageReceived(GuildMessageReceivedEvent event) {

		String[] command = event.getMessage().getContentRaw().split(" ", 2);

		TextChannel channel = event.getChannel();

		if ("!재생".equals(command[0]) && (command.length == 2)) {
			VoiceChannel connectedChannel = event.getMember().getVoiceState().getChannel();
			if (connectedChannel == null) {
				channel.sendMessage("검색어를 입력해주세요.").queue();
				return;
			}
			AudioManager audioManager = event.getGuild().getAudioManager();
			audioManager.openAudioConnection(connectedChannel);
			loadAndPlay(event.getChannel(), command[1]);
		} else if ("!종료".equals(command[0])) {
			VoiceChannel connectedChannel = event.getGuild().getSelfMember().getVoiceState().getChannel();
			if (connectedChannel == null) {
				channel.sendMessage("음성채널에 있지 않습니다.").queue();
				return;
			}
			event.getGuild().getAudioManager().closeAudioConnection();
			channel.sendMessage("재생을 종료합니다.").queue();
		} else if ("!스킵".equals(command[0])) {
			skipTrack(event.getChannel());
			channel.sendMessage("다음곡을 재생합니다.").queue();
		}
	}

	private void loadAndPlay(final TextChannel channel, final String trackUrl) {

		GuildMusicManager musicManager = getGuildAudioPlayer(channel.getGuild());

		playerManager.loadItemOrdered(musicManager, trackUrl, new AudioLoadResultHandler() {

			@Override
			public void trackLoaded(AudioTrack track) {

				channel.sendMessage("재생목록\r" + track.getInfo().title).queue();

				play(channel.getGuild(), musicManager, track);
			}

			@Override
			public void playlistLoaded(AudioPlaylist playlist) {

				AudioTrack firstTrack = playlist.getSelectedTrack();

				if (firstTrack == null) {
					firstTrack = playlist.getTracks().get(0);
				}

//				for (int i=0;i<playlist.getTracks().size();i++) {
//					channel.sendMessage(playlist.getTracks().get(i).getInfo().title).queue();
//				}

				play(channel.getGuild(), musicManager, firstTrack);
			}

			@Override
			public void noMatches() {

				channel.sendMessage("검색결과가 없습니다.\r" + trackUrl).queue();
			}

			@Override
			public void loadFailed(FriendlyException exception) {

				channel.sendMessage("재생중에 오류가 발생했습니다.\r" + exception.getMessage()).queue();
			}
		});
	}

	private void play(Guild guild, GuildMusicManager musicManager, AudioTrack track) {
		connectToFirstVoiceChannel(guild.getAudioManager());

		musicManager.scheduler.queue(track);
	}

	private void skipTrack(TextChannel channel) {
		GuildMusicManager musicManager = getGuildAudioPlayer(channel.getGuild());
		musicManager.scheduler.nextTrack();

		channel.sendMessage("Skipped to next track.").queue();
	}

	@SuppressWarnings("deprecation")
	private static void connectToFirstVoiceChannel(AudioManager audioManager) {
		if (!audioManager.isConnected() && !audioManager.isAttemptingToConnect()) {
			for (VoiceChannel voiceChannel : audioManager.getGuild().getVoiceChannels()) {
				audioManager.openAudioConnection(voiceChannel);
				break;
			}
		}
	}
}
