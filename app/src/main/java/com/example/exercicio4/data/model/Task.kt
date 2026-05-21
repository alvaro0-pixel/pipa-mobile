package com.example.exercicio4.data.model

import android.os.Parcelable
import com.example.exercicio4.util.FirebaseHelper
import com.google.firebase.Firebase
import kotlinx.parcelize.Parcelize

@Parcelize
data class Task (
    var id: String = "",
    var description: String = "",
    var status: Status = Status.TODO
): Parcelable {
    init {
        this.id = FirebaseHelper.getDatabase().push().key ?: ""
    }
}