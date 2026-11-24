package com.zmr.rent.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.zmr.rent.data.entity.TeamMember
import com.zmr.rent.data.entity.User
import com.zmr.rent.data.entity.UserSummary
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {

    @Query("SELECT id, name, role, employeeCode FROM users ORDER BY name COLLATE NOCASE, id")
    fun observeAll(): Flow<List<UserSummary>>

    @Query("SELECT EXISTS(SELECT 1 FROM users WHERE role = 'CEO')")
    fun observeHasCeo(): Flow<Boolean>

    @Query("SELECT * FROM users WHERE id = :userId")
    suspend fun findById(userId: Long): User?

    /** Names are unique ignoring letter case, so "Imran" and "imran" cannot both exist. */
    @Query("SELECT COUNT(*) FROM users WHERE name = :name COLLATE NOCASE")
    suspend fun countWithName(name: String): Int

    @Query("SELECT COUNT(*) FROM users WHERE employeeCode = :employeeCode")
    suspend fun countWithCode(employeeCode: String): Int

    @Insert
    suspend fun insert(user: User): Long

    @Query("UPDATE users SET email = :email WHERE id = :userId")
    suspend fun updateEmail(userId: Long, email: String)

    // Team members invited from this phone.

    @Query("SELECT * FROM team_members WHERE addedByCode = :addedByCode ORDER BY name COLLATE NOCASE")
    fun observeTeam(addedByCode: String): Flow<List<TeamMember>>

    @Query("SELECT * FROM team_members WHERE addedByCode = :addedByCode ORDER BY name COLLATE NOCASE")
    suspend fun getTeam(addedByCode: String): List<TeamMember>

    @Query("SELECT COUNT(*) FROM team_members WHERE employeeCode = :employeeCode")
    suspend fun countTeamMembersWithCode(employeeCode: String): Int

    @Insert
    suspend fun insertTeamMember(member: TeamMember)
}
