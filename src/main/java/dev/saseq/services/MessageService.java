package dev.saseq.services;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MessageService {

    private final JDA jda;

    public MessageService(JDA jda) {
        this.jda = jda;
    }

    /**
     * Resolves a MessageChannel by ID — checks threads first, then text channels.
     */
    private MessageChannel resolveChannel(String channelId) {
        ThreadChannel thread = jda.getThreadChannelById(channelId);
        if (thread != null) return thread;
        TextChannel text = jda.getTextChannelById(channelId);
        if (text != null) return text;
        throw new IllegalArgumentException("Channel or thread not found by ID: " + channelId);
    }

    /**
     * Sends a message to a specified Discord channel or thread.
     *
     * @param channelId The ID of the channel or thread where the message will be sent.
     * @param message   The content of the message to be sent.
     * @return A confirmation message with a link to the sent message.
     */
    @Tool(name = "send_message", description = "Send a message to a specific channel or thread")
    public String sendMessage(@ToolParam(description = "Discord channel or thread ID") String channelId,
                              @ToolParam(description = "Message content") String message) {
        if (channelId == null || channelId.isEmpty()) {
            throw new IllegalArgumentException("channelId cannot be null");
        }
        if (message == null || message.isEmpty()) {
            throw new IllegalArgumentException("message cannot be null");
        }

        MessageChannel channel = resolveChannel(channelId);
        Message sentMessage = channel.sendMessage(message).complete();
        return "Message sent successfully. Message link: " + sentMessage.getJumpUrl();
    }

    /**
     * Edits an existing message in a specified Discord channel.
     *
     * @param channelId  The ID of the channel containing the message.
     * @param messageId  The ID of the message to be edited.
     * @param newMessage The new content for the message.
     * @return A confirmation message with a link to the edited message.
     */
    @Tool(name = "edit_message", description = "Edit a message from a specific channel")
    public String editMessage(@ToolParam(description = "Discord channel ID") String channelId,
                              @ToolParam(description = "Specific message ID") String messageId,
                              @ToolParam(description = "New message content") String newMessage) {
        if (channelId == null || channelId.isEmpty()) {
            throw new IllegalArgumentException("channelId cannot be null");
        }
        if (messageId == null || messageId.isEmpty()) {
            throw new IllegalArgumentException("messageId cannot be null");
        }
        if (newMessage == null || newMessage.isEmpty()) {
            throw new IllegalArgumentException("newMessage cannot be null");
        }

        MessageChannel channel = resolveChannel(channelId);
        Message messageById = channel.retrieveMessageById(messageId).complete();
        if (messageById == null) {
            throw new IllegalArgumentException("Message not found by messageId");
        }
        Message editedMessage = messageById.editMessage(newMessage).complete();
        return "Message edited successfully. Message link: " + editedMessage.getJumpUrl();
    }

    /**
     * Deletes a message from a specified Discord channel.
     *
     * @param channelId The ID of the channel containing the message.
     * @param messageId The ID of the message to be deleted.
     * @return A confirmation message indicating the message was deleted successfully.
     */
    @Tool(name = "delete_message", description = "Delete a message from a specific channel")
    public String deleteMessage(@ToolParam(description = "Discord channel ID") String channelId,
                                @ToolParam(description = "Specific message ID") String messageId) {
        if (channelId == null || channelId.isEmpty()) {
            throw new IllegalArgumentException("channelId cannot be null");
        }
        if (messageId == null || messageId.isEmpty()) {
            throw new IllegalArgumentException("messageId cannot be null");
        }

        MessageChannel channel = resolveChannel(channelId);
        Message messageById = channel.retrieveMessageById(messageId).complete();
        if (messageById == null) {
            throw new IllegalArgumentException("Message not found by messageId");
        }
        messageById.delete().queue();
        return "Message deleted successfully";
    }

    /**
     * Reads the recent message history from a specified Discord channel.
     *
     * @param channelId The ID of the channel from which to read messages.
     * @param count     Optional number of messages to retrieve (default is 100).
     * @return A formatted string containing the retrieved messages.
     */
    @Tool(name = "read_messages", description = "Read recent message history from a specific channel or thread")
    public String readMessages(@ToolParam(description = "Discord channel or thread ID") String channelId,
                               @ToolParam(description = "Number of messages to retrieve", required = false) String count) {
        if (channelId == null || channelId.isEmpty()) {
            throw new IllegalArgumentException("channelId cannot be null");
        }
        int limit = 100;
        if (count != null) {
            limit = Integer.parseInt(count);
        }

        MessageChannel channel = resolveChannel(channelId);
        List<Message> messages = channel.getHistory().retrievePast(limit).complete();
        List<String> formatedMessages = formatMessages(messages);
        return "**Retrieved " + messages.size() + " messages:** \n" + String.join("\n", formatedMessages);
    }

    /**
     * Reads message history from a specified Discord channel before a specific message ID.
     * This allows pagination through older messages.
     *
     * @param channelId  The ID of the channel from which to read messages.
     * @param beforeId   The message ID to retrieve messages before (for pagination).
     * @param count      Optional number of messages to retrieve (default is 100, max 100).
     * @return A formatted string containing the retrieved messages.
     */
    @Tool(name = "read_messages_before", description = "Read message history before a specific message ID (for pagination to older messages)")
    public String readMessagesBefore(@ToolParam(description = "Discord channel or thread ID") String channelId,
                                     @ToolParam(description = "Message ID to retrieve messages before (older messages)") String beforeId,
                                     @ToolParam(description = "Number of messages to retrieve (max 100)", required = false) String count) {
        if (channelId == null || channelId.isEmpty()) {
            throw new IllegalArgumentException("channelId cannot be null");
        }
        if (beforeId == null || beforeId.isEmpty()) {
            throw new IllegalArgumentException("beforeId cannot be null");
        }

        int limit = 100;
        if (count != null) {
            limit = Math.min(Integer.parseInt(count), 100);
        }

        MessageChannel channel = resolveChannel(channelId);
        List<Message> messages = channel.getHistoryBefore(beforeId, limit).complete().getRetrievedHistory();
        List<String> formatedMessages = formatMessages(messages);

        String oldestId = messages.isEmpty() ? "none" : messages.get(messages.size() - 1).getId();
        return String.format("**Retrieved %d messages before ID %s**\nOldest message ID in batch: %s\n%s",
                           messages.size(), beforeId, oldestId, String.join("\n", formatedMessages));
    }

    /**
     * Adds a reaction (emoji) to a specific message in a Discord channel.
     *
     * @param channelId The ID of the channel containing the message.
     * @param messageId The ID of the message to which the reaction will be added.
     * @param emoji     The emoji to add as a reaction (can be a Unicode character or a custom emoji string).
     * @return A confirmation message with a link to the message that was reacted to.
     */
    @Tool(name = "add_reaction", description = "Add a reaction (emoji) to a specific message")
    public String addReaction(@ToolParam(description = "Discord channel ID") String channelId,
                              @ToolParam(description = "Discord message ID") String messageId,
                              @ToolParam(description = "Emoji (Unicode or string)") String emoji) {
        if (channelId == null || channelId.isEmpty()) {
            throw new IllegalArgumentException("channelId cannot be null");
        }
        if (messageId == null || messageId.isEmpty()) {
            throw new IllegalArgumentException("messageId cannot be null");
        }
        if (emoji == null || emoji.isEmpty()) {
            throw new IllegalArgumentException("emoji cannot be null");
        }

        MessageChannel channel = resolveChannel(channelId);
        Message message = channel.retrieveMessageById(messageId).complete();
        if (message == null) {
            throw new IllegalArgumentException("Message not found by messageId");
        }
        message.addReaction(Emoji.fromUnicode(emoji)).queue();
        return "Added reaction successfully. Message link: " + message.getJumpUrl();
    }

    /**
     * Removes a specified reaction (emoji) from a message in a Discord channel.
     *
     * @param channelId The ID of the channel containing the message.
     * @param messageId The ID of the message from which the reaction will be removed.
     * @param emoji     The emoji to remove from the message (can be a Unicode character or a custom emoji string).
     * @return A confirmation message with a link to the message.
     */
    @Tool(name = "remove_reaction", description = "Remove a specified reaction (emoji) from a message")
    public String removeReaction(@ToolParam(description = "Discord channel ID") String channelId,
                                 @ToolParam(description = "Discord message ID") String messageId,
                                 @ToolParam(description = "Emoji (Unicode or string)") String emoji) {
        if (channelId == null || channelId.isEmpty()) {
            throw new IllegalArgumentException("channelId cannot be null");
        }
        if (messageId == null || messageId.isEmpty()) {
            throw new IllegalArgumentException("messageId cannot be null");
        }
        if (emoji == null || emoji.isEmpty()) {
            throw new IllegalArgumentException("emoji cannot be null");
        }

        MessageChannel channel = resolveChannel(channelId);
        Message message = channel.retrieveMessageById(messageId).complete();
        if (message == null) {
            throw new IllegalArgumentException("Message not found by messageId");
        }
        message.removeReaction(Emoji.fromUnicode(emoji)).queue();
        return "Removed reaction successfully. Message link: " + message.getJumpUrl();
    }

    /**
     * Searches for messages in a channel that contain specified text.
     * Searches through up to maxMessages (default 500) recent messages.
     *
     * @param channelId   The ID of the channel to search in.
     * @param searchText  The text to search for (case-insensitive).
     * @param maxMessages Optional maximum number of messages to search through (default 500).
     * @return A formatted string with matching messages.
     */
    @Tool(name = "search_messages", description = "Search for messages containing specific text in a channel")
    public String searchMessages(@ToolParam(description = "Discord channel or thread ID") String channelId,
                                  @ToolParam(description = "Text to search for (case-insensitive)") String searchText,
                                  @ToolParam(description = "Maximum number of recent messages to search (default 500)", required = false) String maxMessages) {
        if (channelId == null || channelId.isEmpty()) {
            throw new IllegalArgumentException("channelId cannot be null");
        }
        if (searchText == null || searchText.isEmpty()) {
            throw new IllegalArgumentException("searchText cannot be null");
        }

        int maxToSearch = 500;
        if (maxMessages != null) {
            maxToSearch = Integer.parseInt(maxMessages);
        }

        MessageChannel channel = resolveChannel(channelId);
        String searchLower = searchText.toLowerCase();
        List<Message> matchingMessages = new java.util.ArrayList<>();

        // Search in batches of 100 messages
        String lastMessageId = null;
        int totalSearched = 0;

        while (totalSearched < maxToSearch) {
            int batchSize = Math.min(100, maxToSearch - totalSearched);
            List<Message> batch;

            if (lastMessageId == null) {
                batch = channel.getHistory().retrievePast(batchSize).complete();
            } else {
                batch = channel.getHistoryBefore(lastMessageId, batchSize).complete().getRetrievedHistory();
            }

            if (batch.isEmpty()) {
                break; // No more messages
            }

            // Filter messages containing search text
            batch.stream()
                    .filter(m -> m.getContentDisplay().toLowerCase().contains(searchLower))
                    .forEach(matchingMessages::add);

            lastMessageId = batch.get(batch.size() - 1).getId();
            totalSearched += batch.size();
        }

        if (matchingMessages.isEmpty()) {
            return String.format("No messages found containing \"%s\" in the last %d messages searched.", searchText, totalSearched);
        }

        List<String> formattedMessages = formatMessages(matchingMessages);
        return String.format("**Found %d messages containing \"%s\" (searched %d messages):**\n%s",
                           matchingMessages.size(), searchText, totalSearched, String.join("\n", formattedMessages));
    }

    private List<String> formatMessages(List<Message> messages) {
        return messages.stream()
                .map(m -> {
                    String authorName = m.getAuthor().getName();
                    String timestamp = m.getTimeCreated().toString();
                    String content = m.getContentDisplay();
                    String messageId = m.getId();

                    return String.format("- (ID: %s) **[%s]** `%s`: ```%s```", messageId, authorName, timestamp, content);
                }).toList();
    }
}