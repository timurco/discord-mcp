package dev.saseq.services;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ChannelService {

    private final JDA jda;

    @Value("${DISCORD_GUILD_ID:}")
    private String defaultGuildId;

    public ChannelService(JDA jda) {
        this.jda = jda;
    }

    private String resolveGuildId(String guildId) {
        if ((guildId == null || guildId.isEmpty()) && defaultGuildId != null && !defaultGuildId.isEmpty()) {
            return defaultGuildId;
        }
        return guildId;
    }

    /**
     * Deletes a channel in a specified Discord server.
     *
     * @param guildId   Optional ID of the Discord server (guild). If not provided, the default server will be used.
     * @param channelId The ID of the channel to be deleted.
     * @return A confirmation message with the name and type of the deleted channel.
     */
    @Tool(name = "delete_channel", description = "Delete a channel")
    public String deleteChannel(@ToolParam(description = "Discord server ID", required = false) String guildId,
                                @ToolParam(description = "Discord channel ID") String channelId) {
        guildId = resolveGuildId(guildId);
        if (guildId == null || guildId.isEmpty()) {
            throw new IllegalArgumentException("guildId cannot be null");
        }
        if (channelId == null || channelId.isEmpty()) {
            throw new IllegalArgumentException("channelId cannot be null");
        }

        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            throw new IllegalArgumentException("Discord server not found by guildId");
        }
        GuildChannel channel = guild.getGuildChannelById(channelId);
        if (channel == null) {
            throw new IllegalArgumentException("Channel not found by channelId");
        }
        channel.delete().queue();
        return "Deleted " + channel.getType().name() + " channel: " + channel.getName();
    }

    /**
     * Creates a new text channel in a specified Discord server.
     *
     * @param guildId    Optional ID of the Discord server (guild). If not provided, the default server will be used.
     * @param name       The name for the new text channel.
     * @param categoryId Optional ID of the category to which the new channel should be added.
     * @return A confirmation message with the name and ID of the created channel, and category if specified.
     */
    @Tool(name = "create_text_channel", description = "Create a new text channel")
    public String createTextChannel(@ToolParam(description = "Discord server ID", required = false) String guildId,
                                    @ToolParam(description = "Channel name") String name,
                                    @ToolParam(description = "Category ID (optional)", required = false) String categoryId) {
        guildId = resolveGuildId(guildId);
        if (guildId == null || guildId.isEmpty()) {
            throw new IllegalArgumentException("guildId cannot be null");
        }
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("name cannot be null");
        }

        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            throw new IllegalArgumentException("Discord server not found by guildId");
        }

        TextChannel textChannel;
        if (categoryId != null && !categoryId.isEmpty()) {
            Category category = guild.getCategoryById(categoryId);
            if (category == null) {
                throw new IllegalArgumentException("Category not found by categoryId");
            }
            textChannel = category.createTextChannel(name).complete();
            return "Created new text channel: " + textChannel.getName() + " (ID: " + textChannel.getId() + ") in category: " + category.getName();
        } else {
            textChannel = guild.createTextChannel(name).complete();
            return "Created new text channel: " + textChannel.getName() + " (ID: " + textChannel.getId() + ")";
        }
    }

    /**
     * Finds a channel by its name within a specified Discord server and returns its type and ID.
     *
     * @param guildId     Optional ID of the Discord server (guild). If not provided, the default server will be used.
     * @param channelName The name of the channel to find.
     * @return A message containing the type, name, and ID of the found channel. If multiple channels are found, it returns a list of them.
     */
    @Tool(name = "find_channel", description = "Find a channel type and ID using name and server ID")
    public String findChannel(@ToolParam(description = "Discord server ID", required = false) String guildId,
                              @ToolParam(description = "Discord category name") String channelName) {
        guildId = resolveGuildId(guildId);
        if (guildId == null || guildId.isEmpty()) {
            throw new IllegalArgumentException("guildId cannot be null");
        }
        if (channelName == null || channelName.isEmpty()) {
            throw new IllegalArgumentException("channelName cannot be null");
        }

        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            throw new IllegalArgumentException("Discord server not found by guildId");
        }
        List<GuildChannel> channels = guild.getChannels();
        if (channels.isEmpty()) {
            throw new IllegalArgumentException("No channels found by guildId");
        }
        List<GuildChannel> filteredChannels = channels.stream().filter(c -> c.getName().equalsIgnoreCase(channelName)).toList();
        if (filteredChannels.isEmpty()) {
            throw new IllegalArgumentException("No channels found with name " + channelName);
        }
        if (filteredChannels.size() > 1) {
            return "Retrieved " + channels.size() + " channels:\n" +
                    channels.stream()
                            .map(c -> "- " + c.getType().name() + " channel: " + c.getName() + " (ID: " + c.getId() + ")")
                            .collect(Collectors.joining("\n"));
        }
        GuildChannel channel = filteredChannels.get(0);
        return "Retrieved " + channel.getType().name() + " channel: " + channel.getName() + " (ID: " + channel.getId() + ")";
    }

    /**
     * Lists all channels in a specified Discord server.
     *
     * @param guildId Optional ID of the Discord server (guild). If not provided, the default server will be used.
     * @return A formatted string listing all channels in the server, including their type, name, and ID.
     */
    @Tool(name = "list_channels", description = "List of all channels")
    public String listChannels(@ToolParam(description = "Discord server ID", required = false) String guildId) {
        guildId = resolveGuildId(guildId);
        if (guildId == null || guildId.isEmpty()) {
            throw new IllegalArgumentException("guildId cannot be null");
        }

        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            throw new IllegalArgumentException("Discord server not found by guildId");
        }
        List<GuildChannel> channels = guild.getChannels();
        if (channels.isEmpty()) {
            throw new IllegalArgumentException("No channels found by guildId");
        }
        return "Retrieved " + channels.size() + " channels:\n" +
                channels.stream()
                        .map(c -> "- " + c.getType().name() + " channel: " + c.getName() + " (ID: " + c.getId() + ")")
                        .collect(Collectors.joining("\n"));
    }

    /**
     * Sets a channel to read-only mode for @everyone role, preventing regular users from sending messages.
     *
     * @param guildId   Optional ID of the Discord server (guild). If not provided, the default server will be used.
     * @param channelId The ID of the channel to modify.
     * @param readonly  If true, denies send message permission for @everyone. If false, resets to default.
     * @return A confirmation message indicating the channel's new permission state.
     */
    @Tool(name = "set_channel_readonly", description = "Make a channel read-only for @everyone (only admins can post)")
    public String setChannelReadonly(@ToolParam(description = "Discord server ID", required = false) String guildId,
                                     @ToolParam(description = "Discord channel ID") String channelId,
                                     @ToolParam(description = "True to make read-only, false to allow everyone to post") boolean readonly) {
        guildId = resolveGuildId(guildId);
        if (guildId == null || guildId.isEmpty()) {
            throw new IllegalArgumentException("guildId cannot be null");
        }
        if (channelId == null || channelId.isEmpty()) {
            throw new IllegalArgumentException("channelId cannot be null");
        }

        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            throw new IllegalArgumentException("Discord server not found by guildId");
        }

        GuildChannel channel = guild.getGuildChannelById(channelId);
        if (channel == null) {
            throw new IllegalArgumentException("Channel not found by channelId");
        }

        Role everyoneRole = guild.getPublicRole();

        if (readonly) {
            channel.getPermissionContainer()
                    .upsertPermissionOverride(everyoneRole)
                    .deny(Permission.MESSAGE_SEND)
                    .complete();
            return "Channel #" + channel.getName() + " is now read-only for @everyone";
        } else {
            channel.getPermissionContainer()
                    .upsertPermissionOverride(everyoneRole)
                    .clear(Permission.MESSAGE_SEND)
                    .complete();
            return "Channel #" + channel.getName() + " now allows @everyone to send messages";
        }
    }

    /**
     * Sets the topic (description) of a text channel.
     *
     * @param guildId   Optional ID of the Discord server (guild). If not provided, the default server will be used.
     * @param channelId The ID of the text channel to modify.
     * @param topic     The new topic/description for the channel (max 1024 characters). Empty string to clear.
     * @return A confirmation message indicating the channel's new topic.
     */
    @Tool(name = "set_channel_topic", description = "Set the topic (description) of a text channel")
    public String setChannelTopic(@ToolParam(description = "Discord server ID", required = false) String guildId,
                                  @ToolParam(description = "Discord channel ID") String channelId,
                                  @ToolParam(description = "Channel topic/description (max 1024 chars)") String topic) {
        guildId = resolveGuildId(guildId);
        if (guildId == null || guildId.isEmpty()) {
            throw new IllegalArgumentException("guildId cannot be null");
        }
        if (channelId == null || channelId.isEmpty()) {
            throw new IllegalArgumentException("channelId cannot be null");
        }

        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            throw new IllegalArgumentException("Discord server not found by guildId");
        }

        TextChannel channel = guild.getTextChannelById(channelId);
        if (channel == null) {
            throw new IllegalArgumentException("Text channel not found by channelId");
        }

        channel.getManager().setTopic(topic).complete();
        return "Channel #" + channel.getName() + " topic set to: " + (topic.isEmpty() ? "(cleared)" : topic);
    }
}