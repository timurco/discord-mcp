package dev.saseq.services;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.utils.FileUpload;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

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
    @Tool(name = "send_message", description = "Send a message to a specific channel or thread, optionally as a reply")
    public String sendMessage(@ToolParam(description = "Discord channel or thread ID") String channelId,
                              @ToolParam(description = "Message content") String message,
                              @ToolParam(description = "Message ID to reply to (optional)", required = false) String replyToMessageId) {
        if (channelId == null || channelId.isEmpty()) {
            throw new IllegalArgumentException("channelId cannot be null");
        }
        if (message == null || message.isEmpty()) {
            throw new IllegalArgumentException("message cannot be null");
        }

        MessageChannel channel = resolveChannel(channelId);
        var action = channel.sendMessage(message);
        if (replyToMessageId != null && !replyToMessageId.isEmpty()) {
            action = action.setMessageReference(replyToMessageId);
        }
        Message sentMessage = action.complete();
        return "Message sent successfully. Message link: " + sentMessage.getJumpUrl();
    }

    /**
     * Sends a message with file attachments to a specified Discord channel or thread.
     *
     * @param channelId The ID of the channel or thread where the message will be sent.
     * @param message   Optional text content of the message (can be empty if only sending files).
     * @param filePaths Comma-separated absolute file paths to attach.
     * @return A confirmation message with a link to the sent message.
     */
    @Tool(name = "send_message_with_files", description = "Send a message with file attachments to a channel or thread. Supports images, videos, and other files.")
    public String sendMessageWithFiles(@ToolParam(description = "Discord channel or thread ID") String channelId,
                                       @ToolParam(description = "Message content (optional, can be empty)", required = false) String message,
                                       @ToolParam(description = "Comma-separated absolute file paths to attach") String filePaths) {
        if (channelId == null || channelId.isEmpty()) {
            throw new IllegalArgumentException("channelId cannot be null");
        }
        if (filePaths == null || filePaths.isEmpty()) {
            throw new IllegalArgumentException("filePaths cannot be null");
        }

        MessageChannel channel = resolveChannel(channelId);

        List<FileUpload> uploads = new ArrayList<>();
        for (String path : filePaths.split(",")) {
            File file = new File(path.trim());
            if (!file.exists()) {
                throw new IllegalArgumentException("File not found: " + path.trim());
            }
            uploads.add(FileUpload.fromData(file));
        }

        Message sentMessage;
        if (message != null && !message.isEmpty()) {
            sentMessage = channel.sendMessage(message)
                    .addFiles(uploads)
                    .complete();
        } else {
            sentMessage = channel.sendFiles(uploads).complete();
        }
        return "Message with files sent successfully. Message link: " + sentMessage.getJumpUrl();
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

    /**
     * Gets detailed information about a specific message, including reply information.
     *
     * @param channelId The ID of the channel containing the message.
     * @param messageId The ID of the message to get information about.
     * @return A formatted string with message details including reply information if applicable.
     */
    @Tool(name = "get_message_info", description = "Get detailed information about a specific message, including reply information")
    public String getMessageInfo(@ToolParam(description = "Discord channel or thread ID") String channelId,
                                  @ToolParam(description = "Message ID to get info about") String messageId) {
        if (channelId == null || channelId.isEmpty()) {
            throw new IllegalArgumentException("channelId cannot be null");
        }
        if (messageId == null || messageId.isEmpty()) {
            throw new IllegalArgumentException("messageId cannot be null");
        }

        MessageChannel channel = resolveChannel(channelId);
        Message message = channel.retrieveMessageById(messageId).complete();
        if (message == null) {
            throw new IllegalArgumentException("Message not found by messageId");
        }

        StringBuilder info = new StringBuilder();
        info.append(String.format("**Message Info (ID: %s)**\n", messageId));
        info.append(String.format("Author: %s\n", message.getAuthor().getName()));
        info.append(String.format("Timestamp: %s\n", message.getTimeCreated().toString()));
        info.append(String.format("Content: ```%s```\n", message.getContentDisplay()));
        info.append(String.format("Jump URL: %s\n", message.getJumpUrl()));

        // Check if this is a reply to another message
        if (message.getMessageReference() != null) {
            info.append("\n**Reply Information:**\n");
            info.append(String.format("This message is a reply to message ID: %s\n",
                                    message.getMessageReference().getMessageId()));

            // Try to get the referenced message
            try {
                Message referencedMessage = message.getReferencedMessage();
                if (referencedMessage != null) {
                    info.append(String.format("Original message author: %s\n",
                                            referencedMessage.getAuthor().getName()));
                    info.append(String.format("Original message content: ```%s```\n",
                                            referencedMessage.getContentDisplay()));
                    info.append(String.format("Original message link: %s\n",
                                            referencedMessage.getJumpUrl()));
                }
            } catch (Exception e) {
                info.append("(Could not retrieve referenced message details)\n");
            }
        } else {
            info.append("\nThis message is not a reply to another message.\n");
        }

        // Include attachments
        if (!message.getAttachments().isEmpty()) {
            info.append("\n**Attachments:**\n");
            for (net.dv8tion.jda.api.entities.Message.Attachment attachment : message.getAttachments()) {
                info.append(String.format("- %s (%s): %s\n",
                    attachment.getFileName(),
                    attachment.getContentType() != null ? attachment.getContentType() : "unknown",
                    attachment.getUrl()));
            }
        }

        // Include embeds
        if (!message.getEmbeds().isEmpty()) {
            info.append("\n**Embeds:**\n");
            for (net.dv8tion.jda.api.entities.MessageEmbed embed : message.getEmbeds()) {
                if (embed.getTitle() != null) info.append(String.format("- Title: %s\n", embed.getTitle()));
                if (embed.getDescription() != null) info.append(String.format("  Description: %s\n", embed.getDescription()));
                if (embed.getUrl() != null) info.append(String.format("  URL: %s\n", embed.getUrl()));
                if (embed.getImage() != null) info.append(String.format("  Image: %s\n", embed.getImage().getUrl()));
                if (embed.getThumbnail() != null) info.append(String.format("  Thumbnail: %s\n", embed.getThumbnail().getUrl()));
            }
        }

        return info.toString();
    }

    @Tool(name = "download_media", description = "Download all image/video attachments from a Discord message to /tmp/ and return local file paths")
    public String downloadMedia(@ToolParam(description = "Discord channel or thread ID") String channelId,
                                @ToolParam(description = "Message ID to download attachments from") String messageId) {
        MessageChannel channel = resolveChannel(channelId);
        if (channel == null) return "Error: Channel not found: " + channelId;

        Message message;
        try {
            message = channel.retrieveMessageById(messageId).complete();
        } catch (Exception e) {
            return "Error: Could not retrieve message: " + e.getMessage();
        }

        if (message.getAttachments().isEmpty()) {
            return "No attachments found on this message.";
        }

        StringBuilder result = new StringBuilder();
        result.append(String.format("**Downloaded %d attachment(s):**\n", message.getAttachments().size()));

        for (Message.Attachment attachment : message.getAttachments()) {
            String fileName = messageId + "_" + attachment.getFileName();
            Path targetPath = Path.of("/tmp", fileName);
            try {
                CompletableFuture<InputStream> future = attachment.getProxy().download();
                try (InputStream is = future.join()) {
                    Files.copy(is, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }
                result.append(String.format("- %s → %s (%s, %s)\n",
                    attachment.getFileName(), targetPath,
                    attachment.getContentType() != null ? attachment.getContentType() : "unknown",
                    formatSize(attachment.getSize())));
            } catch (Exception e) {
                result.append(String.format("- %s → FAILED: %s\n", attachment.getFileName(), e.getMessage()));
            }
        }

        return result.toString();
    }

    private String formatSize(int bytes) {
        if (bytes < 1024) return bytes + "B";
        if (bytes < 1024 * 1024) return String.format("%.1fKB", bytes / 1024.0);
        return String.format("%.1fMB", bytes / (1024.0 * 1024.0));
    }

    /**
     * Gets all replies to a specific message by searching through message history.
     *
     * @param channelId    The ID of the channel containing the message.
     * @param messageId    The ID of the message to find replies for.
     * @param maxMessages  Optional maximum number of messages to search (default 200).
     * @return A formatted string with all replies to the specified message.
     */
    @Tool(name = "get_message_replies", description = "Get all replies to a specific message")
    public String getMessageReplies(@ToolParam(description = "Discord channel or thread ID") String channelId,
                                     @ToolParam(description = "Message ID to find replies for") String messageId,
                                     @ToolParam(description = "Maximum number of messages to search (default 200)", required = false) String maxMessages) {
        if (channelId == null || channelId.isEmpty()) {
            throw new IllegalArgumentException("channelId cannot be null");
        }
        if (messageId == null || messageId.isEmpty()) {
            throw new IllegalArgumentException("messageId cannot be null");
        }

        int maxToSearch = 200;
        if (maxMessages != null) {
            maxToSearch = Integer.parseInt(maxMessages);
        }

        MessageChannel channel = resolveChannel(channelId);
        List<Message> replies = new java.util.ArrayList<>();

        // Search for messages that reply to the specified message
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
                break;
            }

            // Filter messages that are replies to our target message
            batch.stream()
                    .filter(m -> m.getMessageReference() != null
                              && messageId.equals(m.getMessageReference().getMessageId()))
                    .forEach(replies::add);

            lastMessageId = batch.get(batch.size() - 1).getId();
            totalSearched += batch.size();
        }

        if (replies.isEmpty()) {
            return String.format("No replies found to message %s (searched %d messages).", messageId, totalSearched);
        }

        List<String> formattedReplies = formatMessages(replies);
        return String.format("**Found %d replies to message %s (searched %d messages):**\n%s",
                           replies.size(), messageId, totalSearched, String.join("\n", formattedReplies));
    }

    /**
     * Lists all threads (active and archived) in a specified channel.
     *
     * @param channelId The ID of the channel to list threads from.
     * @return A formatted string with thread information.
     */
    @Tool(name = "list_threads", description = "List all threads in a channel")
    public String listThreads(@ToolParam(description = "Channel ID to list threads from") String channelId) {
        if (channelId == null || channelId.isEmpty()) {
            throw new IllegalArgumentException("channelId cannot be null");
        }

        TextChannel channel = jda.getTextChannelById(channelId);
        if (channel == null) {
            throw new IllegalArgumentException("Channel not found by ID: " + channelId);
        }

        List<ThreadChannel> threads = new java.util.ArrayList<>();

        // Get active threads
        threads.addAll(channel.getThreadChannels());

        // Get archived public threads
        try {
            List<ThreadChannel> archivedPublic = channel.retrieveArchivedPublicThreadChannels().complete();
            threads.addAll(archivedPublic);
        } catch (Exception e) {
            // Continue if archived threads retrieval fails
        }

        if (threads.isEmpty()) {
            return String.format("No threads found in channel %s", channelId);
        }

        StringBuilder result = new StringBuilder();
        result.append(String.format("**Found %d threads in channel %s:**\n", threads.size(), channelId));

        for (ThreadChannel thread : threads) {
            result.append(String.format("\n- **%s** (ID: %s)\n", thread.getName(), thread.getId()));
            result.append(String.format("  Created: %s\n", thread.getTimeCreated().toString()));
            result.append(String.format("  Archived: %s\n", thread.isArchived()));
            result.append(String.format("  Message count: %d\n", thread.getMessageCount()));
            result.append(String.format("  Jump URL: %s\n", thread.getJumpUrl()));
        }

        return result.toString();
    }

    /**
     * Reads messages from a Discord thread. This is a convenience method that makes it explicit
     * that you're reading from a thread rather than a channel.
     *
     * @param threadId The ID of the thread to read messages from.
     * @param count    Optional number of messages to retrieve (default is 100).
     * @return A formatted string containing the retrieved messages.
     */
    @Tool(name = "read_thread_messages", description = "Read messages from a Discord thread. Threads are conversations branched from a message.")
    public String readThreadMessages(@ToolParam(description = "Thread ID") String threadId,
                                      @ToolParam(description = "Number of messages to retrieve", required = false) String count) {
        if (threadId == null || threadId.isEmpty()) {
            throw new IllegalArgumentException("threadId cannot be null");
        }

        // Verify it's actually a thread
        ThreadChannel thread = jda.getThreadChannelById(threadId);
        if (thread == null) {
            throw new IllegalArgumentException("Thread not found by ID: " + threadId);
        }

        // Use the existing readMessages method which already supports threads via resolveChannel
        return readMessages(threadId, count);
    }

    /**
     * Gets detailed information about a specific thread.
     *
     * @param threadId The ID of the thread to get information about.
     * @return A formatted string with thread details.
     */
    @Tool(name = "get_thread_info", description = "Get information about a specific thread")
    public String getThreadInfo(@ToolParam(description = "Thread ID") String threadId) {
        if (threadId == null || threadId.isEmpty()) {
            throw new IllegalArgumentException("threadId cannot be null");
        }

        ThreadChannel thread = jda.getThreadChannelById(threadId);
        if (thread == null) {
            throw new IllegalArgumentException("Thread not found by ID: " + threadId);
        }

        StringBuilder info = new StringBuilder();
        info.append(String.format("**Thread Info (ID: %s)**\n", threadId));
        info.append(String.format("Name: %s\n", thread.getName()));
        info.append(String.format("Parent Channel: %s (%s)\n",
                                thread.getParentChannel().getName(),
                                thread.getParentChannel().getId()));
        info.append(String.format("Created: %s\n", thread.getTimeCreated().toString()));
        info.append(String.format("Owner: %s\n", thread.getOwner() != null ? thread.getOwner().getUser().getName() : "Unknown"));
        info.append(String.format("Archived: %s\n", thread.isArchived()));
        info.append(String.format("Locked: %s\n", thread.isLocked()));
        info.append(String.format("Message Count: %d\n", thread.getMessageCount()));
        info.append(String.format("Member Count: %d\n", thread.getMemberCount()));
        info.append(String.format("Jump URL: %s\n", thread.getJumpUrl()));

        return info.toString();
    }

    private List<String> formatMessages(List<Message> messages) {
        return messages.stream()
                .map(m -> {
                    String authorName = m.getAuthor().getName();
                    String timestamp = m.getTimeCreated().toString();
                    String content = m.getContentDisplay();
                    String messageId = m.getId();

                    // Add reply indicator if this is a reply
                    String replyInfo = "";
                    if (m.getMessageReference() != null) {
                        replyInfo = String.format(" [Reply to: %s]", m.getMessageReference().getMessageId());
                    }

                    // Add attachment URLs if present
                    String attachmentInfo = "";
                    if (!m.getAttachments().isEmpty()) {
                        attachmentInfo = " [Attachments: " + m.getAttachments().stream()
                            .map(a -> a.getUrl())
                            .reduce((a, b) -> a + " , " + b)
                            .orElse("") + "]";
                    }

                    return String.format("- (ID: %s) **[%s]** `%s`%s: ```%s```%s",
                                       messageId, authorName, timestamp, replyInfo, content, attachmentInfo);
                }).toList();
    }
}