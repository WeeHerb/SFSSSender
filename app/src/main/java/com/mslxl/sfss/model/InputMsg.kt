package com.mslxl.sfss.model

sealed class InputMsg{
    companion object{
        fun from(msg: ByteArray) : InputMsg{
            error("")
        }
    }
}