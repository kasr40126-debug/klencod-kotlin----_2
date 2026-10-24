
package com.kaser.alkaserapp

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.*
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class MainActivity : Activity() {

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    private lateinit var messages: LinearLayout
    private lateinit var input: EditText
    private lateinit var send: Button
    private lateinit var status: TextView
    private var listener: com.google.firebase.firestore.ListenerRegistration? = null
    private var nickname = "مستخدم الكاسر"

    private val green = Color.rgb(7, 59, 50)
    private val gold = Color.rgb(227, 189, 105)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = green
        window.navigationBarColor = green

        try {
            FirebaseApp.initializeApp(this)
            showLogin()
        } catch (e: Exception) {
            errorScreen("تعذر تشغيل Firebase. تأكد من إعداد المشروع وملف google-services.json")
        }
    }

    private fun showLogin() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28, 24, 28, 24)
            setBackgroundColor(Color.rgb(245, 242, 233))
        }

        root.addView(TextView(this).apply {
            text = "الكاسر آب"
            textSize = 32f
            setTextColor(green)
            gravity = Gravity.CENTER
        })

        root.addView(TextView(this).apply {
            text = "تواصل مع الآخرين عبر الإنترنت"
            textSize = 17f
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 25)
        })

        val name = EditText(this).apply {
            hint = "اكتب اسمك"
            textSize = 17f
            singleLine = true
        }
        root.addView(name)

        val enter = Button(this).apply {
            text = "دخول إلى المحادثة"
            setTextColor(Color.WHITE)
            setBackgroundColor(green)
        }
        root.addView(enter)

        enter.setOnClickListener {
            nickname = name.text.toString().trim()
                .take(30).ifBlank { "مستخدم الكاسر" }
            enter.isEnabled = false

            auth.signInAnonymously()
                .addOnSuccessListener {
                    showChat()
                }
                .addOnFailureListener {
                    enter.isEnabled = true
                    Toast.makeText(
                        this, "فشل الاتصال: ${it.localizedMessage}",
                        Toast.LENGTH_LONG
                    ).show()
                }
        }

        setContentView(root)
    }

    private fun showChat() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(245, 242, 233))
        }

        val header = TextView(this).apply {
            text = "  الكاسر آب  •  المحادثة العامة"
            textSize = 21f
            setTextColor(Color.WHITE)
            setBackgroundColor(green)
            setPadding(12, 20, 12, 20)
        }
        root.addView(header)

        status = TextView(this).apply {
            text = "جارٍ الاتصال..."
            textSize = 13f
            setPadding(12, 8, 12, 8)
        }
        root.addView(status)

        val scroll = ScrollView(this)
        messages = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 8, 12, 8)
        }
        scroll.addView(messages)
        root.addView(scroll, LinearLayout.LayoutParams(
            -1, 0, 1f
        ))

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(6, 6, 6, 6)
        }

        input = EditText(this).apply {
            hint = "اكتب رسالتك..."
            textSize = 16f
            maxLines = 4
        }

        send = Button(this).apply {
            text = "إرسال"
            setTextColor(Color.WHITE)
            setBackgroundColor(green)
        }

        bottom.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        bottom.addView(send)
        root.addView(bottom)

        setContentView(root)

        send.setOnClickListener {
            sendMessage()
        }

        listenMessages()
    }

    private fun sendMessage() {
        val text = input.text.toString().trim()
        val user = auth.currentUser ?: return

        if (text.isBlank()) return
        if (text.length > 2000) {
            Toast.makeText(this, "الرسالة طويلة جدًا", Toast.LENGTH_SHORT).show()
            return
        }

        send.isEnabled = false

        val data = hashMapOf(
            "uid" to user.uid,
            "name" to nickname,
            "text" to text,
            "createdAt" to System.currentTimeMillis()
        )

        db.collection("public_chat")
            .add(data)
            .addOnSuccessListener {
                input.setText("")
                (getSystemService(Context.INPUT_METHOD_SERVICE)
                    as InputMethodManager)
                    .hideSoftInputFromWindow(input.windowToken, 0)
            }
            .addOnFailureListener {
                Toast.makeText(this, "تعذر إرسال الرسالة", Toast.LENGTH_SHORT).show()
            }
            .addOnCompleteListener {
                send.isEnabled = true
            }
    }

    private fun listenMessages() {
        listener = db.collection("public_chat")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .limitToLast(100)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    status.text = "تعذر تحميل الرسائل"
                    return@addSnapshotListener
                }

                if (snapshot == null) return@addSnapshotListener

                status.text = "متصل • ${snapshot.size()} رسالة"

                messages.removeAllViews()

                for (doc in snapshot.documents) {
                    val name = doc.getString("name") ?: "مستخدم"
                    val text = doc.getString("text") ?: ""
                    val uid = doc.getString("uid") ?: ""
                    val mine = uid == auth.currentUser?.uid

                    val bubble = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(14, 10, 14, 10)
                        setBackgroundColor(
                            if (mine) Color.rgb(210, 237, 216)
                            else Color.WHITE
                        )
                    }

                    bubble.addView(TextView(this).apply {
                        this.text = if (mine) "أنت" else name
                        textSize = 12f
                        setTextColor(green)
                    })

                    bubble.addView(TextView(this).apply {
                        this.text = text
                        textSize = 16f
                        setTextColor(Color.BLACK)
                    })

                    val row = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = if (mine) Gravity.END else Gravity.START
                        setPadding(0, 5, 0, 5)
                        addView(bubble, LinearLayout.LayoutParams(
                            -2, -2
                        ).apply {
                            weight = 0f
                        })
                    }

                    messages.addView(row)
                }

                (messages.parent as? ScrollView)?.post {
                    (messages.parent as? ScrollView)?.fullScroll(View.FOCUS_DOWN)
                }
            }
    }

    private fun errorScreen(message: String) {
        setContentView(TextView(this).apply {
            text = message
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(24, 24, 24, 24)
        })
    }

    override fun onDestroy() {
        listener?.remove()
        super.onDestroy()
    }
}