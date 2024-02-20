package com.example.pxfcmapp.models

enum class Postback(val event: String) {
    DEPOSIT("dep"),
    REGISTER("reg")
}