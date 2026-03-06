@file:Suppress("SpellCheckingInspection")

package org.delcom.services

import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import org.delcom.data.AppException
import org.delcom.data.AuthRequest
import org.delcom.data.DataResponse
import org.delcom.data.UserResponse
import org.delcom.helpers.ServiceHelper
import org.delcom.helpers.ValidatorHelper
import org.delcom.helpers.hashPassword
import org.delcom.helpers.verifyPassword
import org.delcom.repositories.IRefreshTokenRepository
import org.delcom.repositories.IUserRepository
import java.io.File
import java.util.*

class UserService(
    private val userRepo: IUserRepository,
    private val refreshTokenRepo: IRefreshTokenRepository,
) {
    suspend fun getMe(call: ApplicationCall) {
        val user = ServiceHelper.getAuthUser(call, userRepo)

        val response = DataResponse(
            "success",
            "Berhasil mengambil informasi akun saya",
            mapOf(
                "user" to UserResponse(
                    id = user.id,
                    name = user.name,
                    username = user.username,
                    about = user.about,
                    createdAt = user.createdAt,
                    updatedAt = user.updatedAt,
                ),
            )
        )
        call.respond(response)
    }

    suspend fun putMe(call: ApplicationCall) {
        val user = ServiceHelper.getAuthUser(call, userRepo)

        val request = call.receive<AuthRequest>()

        val validator = ValidatorHelper(request.toMap())
        validator.required("name", "Nama tidak boleh kosong")
        validator.required("username", "Username tidak boleh kosong")
        validator.validate()

        val existUser = userRepo.getByUsername(request.username)
        if (existUser != null && existUser.username != user.username) {
            throw AppException(
                409,
                "Akun dengan username ini sudah terdaftar!"
            )
        }

        user.username = request.username
        user.name = request.name
        user.about = request.about
        user.updatedAt = Clock.System.now()

        val isUpdated = userRepo.update(
            user.id,
            user
        )
        if (!isUpdated) {
            throw AppException(400, "Gagal memperbarui data profile!")
        }

        val response = DataResponse(
            "success",
            "Berhasil mengubah data profile",
            null
        )
        call.respond(response)
    }

    suspend fun putMyPhoto(call: ApplicationCall) {
        val user = ServiceHelper.getAuthUser(call, userRepo)

        var newPhoto: String? = null
        val multipartData = call.receiveMultipart(formFieldLimit = 1024 * 1024 * 5)
        multipartData.forEachPart { part ->
            when (part) {
                is PartData.FileItem -> {
                    val ext = part.originalFileName
                        ?.substringAfterLast('.', "")
                        ?.let { if (it.isNotEmpty()) ".$it" else "" }
                        ?: ""

                    val fileName = UUID.randomUUID().toString() + ext
                    val filePath = "uploads/users/$fileName"

                    withContext(Dispatchers.IO) {
                        val file = File(filePath)
                        file.parentFile.mkdirs()

                        part.provider().copyAndClose(file.writeChannel())
                        newPhoto = filePath
                    }
                }
                else -> {}
            }
            part.dispose()
        }

        if(newPhoto == null){
            throw AppException(404, "Photo profile tidak tersedia!")
        }

        val newFile = File(newPhoto)
        if (!newFile.exists()) {
            throw AppException(404, "Photo profile gagal diunggah!")
        }

        val oldPhoto = user.photo
        user.photo = newPhoto
        user.updatedAt = Clock.System.now()

        val isUpdated = userRepo.update(
            user.id,
            user
        )
        if (!isUpdated) {
            throw AppException(400, "Gagal memperbarui photo profile!")
        }

        if(oldPhoto != null){
            val oldFile = File(oldPhoto)
            if(oldFile.exists()){
                oldFile.delete()
            }
        }

        val response = DataResponse(
            "success",
            "Berhasil mengubah photo profile",
            null
        )
        call.respond(response)
    }

    suspend fun putMyPassword(call: ApplicationCall) {
        val user = ServiceHelper.getAuthUser(call, userRepo)

        val request = call.receive<AuthRequest>()

        val validator = ValidatorHelper(request.toMap())
        validator.required("newPassword", "Kata sandi baru tidak boleh kosong")
        validator.required("password", "Kata sandi lama tidak boleh kosong")
        validator.validate()

        val validPassword = verifyPassword(request.password, user.password)
        if (!validPassword) {
            throw AppException(404, "Kata sandi lama tidak valid!")
        }

        user.password = hashPassword(request.newPassword)
        user.updatedAt = Clock.System.now()

        val isUpdated = userRepo.update(
            user.id,
            user
        )
        if (!isUpdated) {
            throw AppException(400, "Gagal mengubah kata sandi!")
        }

        refreshTokenRepo.deleteByUserId(user.id)

        val response = DataResponse(
            "success",
            "Berhasil mengubah kata sandi",
            null
        )
        call.respond(response)
    }

    suspend fun getPhoto(call: ApplicationCall) {
        val userId = call.parameters["id"]
            ?: throw AppException(400, "Data todo tidak valid!")

        val user = userRepo.getById(userId) ?: throw AppException(400, "User not found!")

        if(user.photo == null){
            throw AppException(404, "User belum memiliki photo profile")
        }

        val file = File(user.photo!!)
        if (!file.exists()) {
            throw AppException(404, "Photo profile tidak tersedia")
        }

        call.respondFile(file)
    }
}