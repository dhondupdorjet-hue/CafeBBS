package com.bettafish.flarent.network

import com.bettafish.flarent.models.AiChatRequest
import com.bettafish.flarent.models.AiChatResponse
import com.bettafish.flarent.models.Discussion
import com.bettafish.flarent.models.File
import com.bettafish.flarent.models.Forum
import com.bettafish.flarent.models.LoginResponse
import com.bettafish.flarent.models.Message
import com.bettafish.flarent.models.MessageDialog
import com.bettafish.flarent.models.Notification
import com.bettafish.flarent.models.Poll
import com.bettafish.flarent.models.Post
import com.bettafish.flarent.models.PostReactions
import com.bettafish.flarent.models.Tag
import com.bettafish.flarent.models.User
import com.bettafish.flarent.models.request.LoginRequest
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.QueryMap

interface FlarumService {
    // GET /api/discussions
    @GET("api/discussions")
    suspend fun getDiscussionList(
        @QueryMap options: Map<String, String>
    ): List<Discussion>

    // GET /api/discussions/{id}
    @GET("api/discussions/{id}")
    suspend fun getDiscussion(
        @Path("id") id: String,
        @QueryMap options: Map<String, String>
    ): Discussion

    @PATCH("api/discussion/{id}")
    suspend fun patchDiscussion(
        @Path("id") id: String,
        @Body discussion: Discussion
    ) : Discussion?
    @GET("api/tags")
    suspend fun getTags(
        @Query("include") include: String? = "children,lastPostedDiscussion,parent"
    ): List<Tag>

    @GET("api/posts")
    suspend fun getPosts(
        @QueryMap options: Map<String, String>
        ):List<Post>

    @GET("api/users/{id}")
    suspend fun getUser(
        @Path("id") id: String
    ): User

    @GET("api/users")
    suspend fun getUsers(
        @QueryMap request: Map<String, String>
    ): List<User>

    @GET("api")
    suspend fun getForum(): Forum

    @POST("api/token")
    suspend fun getToken(
        @Body body: LoginRequest
    ): LoginResponse

    @GET("api/posts/{id}/reactions")
    suspend fun getReactions(
        @Path("id") id : String,
        @Query("include") include: String? = "user,reaction"
    ): List<PostReactions>

    @PATCH("api/posts/{id}")
    @JvmSuppressWildcards(true)
    suspend fun votePost(
        @Path("id") id: String,
        @Body request: Map<String, Any?>
    ): Post

    @PATCH("api/posts/{id}")
    @JvmSuppressWildcards(true)
    suspend fun reactPost(
        @Path("id") id: String,
        @Body request: Map<String, Any?>
    ): Post

    @POST("api/posts")
    suspend fun sendPost(
        @Body post: Post
    ) : Post

    @PATCH("api/posts/{id}")
    suspend fun patchPost(
        @Path("id") id: String,
        @Body post: Post
    ) : Post

    @PATCH("api/posts/{id}")
    @JvmSuppressWildcards(true)
    suspend fun patchPost(
        @Path("id") id: String,
        @Body request: Map<String, Any?>
    ) : Post

    @retrofit2.http.DELETE("api/posts/{id}")
    suspend fun deletePost(
        @Path("id") id: String
    )

    @PATCH("api/notifications/{id}")
    suspend fun patchNotification(
        @Path("id") id: String,
        @Body post: Notification
    ) : Notification?

    @POST("api/notifications/read")
    suspend fun markAllNotificationsAsRead()

    @Multipart
    @POST("api/fof/upload")
    suspend fun uploadFile(
        @Part file: MultipartBody.Part
    ): List<File>

    @GET("api/notifications")
    suspend fun getNotifications(@QueryMap options:Map<String,String>): List<Notification>

    @POST("api/discussions")
    @JvmSuppressWildcards(true)
    suspend fun startDiscussion(
        @Body request: Map<String, Any?>
    ): Discussion

    // Poll endpoints (fof/polls)
    @GET("api/polls/{id}")
    suspend fun getPoll(
        @Path("id") id: String,
        @Query("include") include: String? = "options,myVotes"
    ): Poll

    @PATCH("api/polls/{id}/vote")
    @JvmSuppressWildcards(true)
    suspend fun votePoll(
        @Path("id") id: String,
        @Body request: Map<String, Any?>
    ): Poll

    @POST("api/polls")
    @JvmSuppressWildcards(true)
    suspend fun createPoll(
        @Body request: Map<String, Any?>
    ): Poll

    @PATCH("api/users/{id}")
    suspend fun patchUser(
        @Path("id") id: String,
        @Body user: User
    ): User

    // Private messages (neoncube/private-messages)
    @GET("api/dialogs")
    suspend fun getDialogs(
        @QueryMap options: Map<String, String>
    ): List<MessageDialog>

    @GET("api/dialogs/{id}")
    suspend fun getDialog(
        @Path("id") id: String,
        @Query("include") include: String? = "messages,messages.user"
    ): MessageDialog

    @GET("api/dialogs/{id}/messages")
    suspend fun getMessages(
        @Path("id") dialogId: String,
        @QueryMap options: Map<String, String>
    ): List<Message>

    @POST("api/messages")
    suspend fun sendMessage(
        @Body message: Message
    ): Message

    // AI 聊天
    @POST("api/ai-proxy/chat")
    suspend fun aiChat(
        @Body body: AiChatRequest,
    ): AiChatResponse
}