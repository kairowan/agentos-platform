package com.agentos.capability.api

import android.os.Parcel
import android.os.Parcelable

class MediaEvent(
    val state: Int,
    val message: String,
    val uri: String = "",
    val durationMillis: Long = 0,
) : Parcelable {
    private constructor(parcel: Parcel) : this(
        parcel.readInt(), parcel.readString().orEmpty(), parcel.readString().orEmpty(), parcel.readLong(),
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(state); parcel.writeString(message); parcel.writeString(uri); parcel.writeLong(durationMillis)
    }
    override fun describeContents() = 0

    companion object CREATOR : Parcelable.Creator<MediaEvent> {
        override fun createFromParcel(parcel: Parcel) = MediaEvent(parcel)
        override fun newArray(size: Int): Array<MediaEvent?> = arrayOfNulls(size)
    }
}
