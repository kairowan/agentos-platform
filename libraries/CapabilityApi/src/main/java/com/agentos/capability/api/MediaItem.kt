package com.agentos.capability.api

import android.os.Parcel
import android.os.Parcelable

class MediaItem(
    val uri: String,
    val displayName: String,
    val mimeType: String,
    val createdAtMillis: Long,
    val durationMillis: Long,
) : Parcelable {
    private constructor(parcel: Parcel) : this(
        parcel.readString().orEmpty(), parcel.readString().orEmpty(), parcel.readString().orEmpty(),
        parcel.readLong(), parcel.readLong(),
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(uri); parcel.writeString(displayName); parcel.writeString(mimeType)
        parcel.writeLong(createdAtMillis); parcel.writeLong(durationMillis)
    }
    override fun describeContents() = 0

    companion object CREATOR : Parcelable.Creator<MediaItem> {
        override fun createFromParcel(parcel: Parcel) = MediaItem(parcel)
        override fun newArray(size: Int): Array<MediaItem?> = arrayOfNulls(size)
    }
}
