package dev.saseq.services;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RoleService {

    private final JDA jda;

    @Value("${DISCORD_GUILD_ID:}")
    private String defaultGuildId;

    public RoleService(JDA jda) {
        this.jda = jda;
    }

    private String resolveGuildId(String guildId) {
        if ((guildId == null || guildId.isEmpty()) && defaultGuildId != null && !defaultGuildId.isEmpty()) {
            return defaultGuildId;
        }
        return guildId;
    }

    /**
     * Creates a new role in a Discord server with optional permissions.
     *
     * @param guildId    Optional ID of the Discord server. If not provided, the default server will be used.
     * @param name       The name for the new role.
     * @param color      Optional hex color code (e.g., "#FF5733"). If not provided, default color is used.
     * @param permissions Optional comma-separated permissions (e.g., "MESSAGE_SEND,MESSAGE_MANAGE").
     * @return A confirmation message with the created role details.
     */
    @Tool(name = "create_role", description = "Create a new role in the server")
    public String createRole(@ToolParam(description = "Discord server ID", required = false) String guildId,
                             @ToolParam(description = "Role name") String name,
                             @ToolParam(description = "Hex color code (e.g., #FF5733)", required = false) String color,
                             @ToolParam(description = "Comma-separated permissions (e.g., MESSAGE_SEND,MANAGE_CHANNELS)", required = false) String permissions) {
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

        var roleAction = guild.createRole().setName(name);

        if (color != null && !color.isEmpty()) {
            try {
                int colorInt = Integer.parseInt(color.replace("#", ""), 16);
                roleAction = roleAction.setColor(colorInt);
            } catch (NumberFormatException e) {
                // Ignore invalid color
            }
        }

        if (permissions != null && !permissions.isEmpty()) {
            String[] perms = permissions.split(",");
            for (String perm : perms) {
                try {
                    Permission permission = Permission.valueOf(perm.trim().toUpperCase());
                    roleAction = roleAction.setPermissions(permission);
                } catch (IllegalArgumentException e) {
                    // Ignore invalid permission
                }
            }
        }

        Role role = roleAction.complete();
        return "Created role: " + role.getName() + " (ID: " + role.getId() + ")";
    }

    /**
     * Lists all roles in a Discord server.
     *
     * @param guildId Optional ID of the Discord server. If not provided, the default server will be used.
     * @return A formatted list of all roles with their IDs.
     */
    @Tool(name = "list_roles", description = "List all roles in the server")
    public String listRoles(@ToolParam(description = "Discord server ID", required = false) String guildId) {
        guildId = resolveGuildId(guildId);
        if (guildId == null || guildId.isEmpty()) {
            throw new IllegalArgumentException("guildId cannot be null");
        }

        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            throw new IllegalArgumentException("Discord server not found by guildId");
        }

        List<Role> roles = guild.getRoles();
        return "Roles (" + roles.size() + "):\n" +
                roles.stream()
                        .map(r -> "- " + r.getName() + " (ID: " + r.getId() + ")")
                        .collect(Collectors.joining("\n"));
    }

    /**
     * Assigns a role to a user in a Discord server.
     *
     * @param guildId Optional ID of the Discord server. If not provided, the default server will be used.
     * @param userId  The ID of the user to assign the role to.
     * @param roleId  The ID of the role to assign.
     * @return A confirmation message.
     */
    @Tool(name = "assign_role", description = "Assign a role to a user")
    public String assignRole(@ToolParam(description = "Discord server ID", required = false) String guildId,
                             @ToolParam(description = "User ID") String userId,
                             @ToolParam(description = "Role ID") String roleId) {
        guildId = resolveGuildId(guildId);
        if (guildId == null || guildId.isEmpty()) {
            throw new IllegalArgumentException("guildId cannot be null");
        }
        if (userId == null || userId.isEmpty()) {
            throw new IllegalArgumentException("userId cannot be null");
        }
        if (roleId == null || roleId.isEmpty()) {
            throw new IllegalArgumentException("roleId cannot be null");
        }

        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            throw new IllegalArgumentException("Discord server not found by guildId");
        }

        Role role = guild.getRoleById(roleId);
        if (role == null) {
            throw new IllegalArgumentException("Role not found by roleId");
        }

        Member member = guild.retrieveMemberById(userId).complete();
        if (member == null) {
            throw new IllegalArgumentException("User not found by userId");
        }

        guild.addRoleToMember(member, role).complete();
        return "Assigned role " + role.getName() + " to user " + member.getUser().getName();
    }

    /**
     * Removes a role from a user in a Discord server.
     *
     * @param guildId Optional ID of the Discord server. If not provided, the default server will be used.
     * @param userId  The ID of the user to remove the role from.
     * @param roleId  The ID of the role to remove.
     * @return A confirmation message.
     */
    @Tool(name = "remove_role", description = "Remove a role from a user")
    public String removeRole(@ToolParam(description = "Discord server ID", required = false) String guildId,
                             @ToolParam(description = "User ID") String userId,
                             @ToolParam(description = "Role ID") String roleId) {
        guildId = resolveGuildId(guildId);
        if (guildId == null || guildId.isEmpty()) {
            throw new IllegalArgumentException("guildId cannot be null");
        }
        if (userId == null || userId.isEmpty()) {
            throw new IllegalArgumentException("userId cannot be null");
        }
        if (roleId == null || roleId.isEmpty()) {
            throw new IllegalArgumentException("roleId cannot be null");
        }

        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            throw new IllegalArgumentException("Discord server not found by guildId");
        }

        Role role = guild.getRoleById(roleId);
        if (role == null) {
            throw new IllegalArgumentException("Role not found by roleId");
        }

        Member member = guild.retrieveMemberById(userId).complete();
        if (member == null) {
            throw new IllegalArgumentException("User not found by userId");
        }

        guild.removeRoleFromMember(member, role).complete();
        return "Removed role " + role.getName() + " from user " + member.getUser().getName();
    }

    /**
     * Deletes a role from a Discord server.
     *
     * @param guildId Optional ID of the Discord server. If not provided, the default server will be used.
     * @param roleId  The ID of the role to delete.
     * @return A confirmation message.
     */
    @Tool(name = "delete_role", description = "Delete a role from the server")
    public String deleteRole(@ToolParam(description = "Discord server ID", required = false) String guildId,
                             @ToolParam(description = "Role ID") String roleId) {
        guildId = resolveGuildId(guildId);
        if (guildId == null || guildId.isEmpty()) {
            throw new IllegalArgumentException("guildId cannot be null");
        }
        if (roleId == null || roleId.isEmpty()) {
            throw new IllegalArgumentException("roleId cannot be null");
        }

        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            throw new IllegalArgumentException("Discord server not found by guildId");
        }

        Role role = guild.getRoleById(roleId);
        if (role == null) {
            throw new IllegalArgumentException("Role not found by roleId");
        }

        String roleName = role.getName();
        role.delete().complete();
        return "Deleted role: " + roleName;
    }
}
