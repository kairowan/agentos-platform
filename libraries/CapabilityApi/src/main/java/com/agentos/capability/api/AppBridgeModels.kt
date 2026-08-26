package com.agentos.capability.api

import android.os.Parcel
import android.os.Parcelable

class AppDescriptor(
    val packageName: String,
    val label: String,
    val category: String,
    val capabilities: List<String>,
) : Parcelable {
    private constructor(parcel: Parcel) : this(
        parcel.readString().orEmpty(), parcel.readString().orEmpty(), parcel.readString().orEmpty(),
        parcel.createStringArrayList().orEmpty(),
    )
    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(packageName); parcel.writeString(label); parcel.writeString(category)
        parcel.writeStringList(capabilities)
    }
    override fun describeContents() = 0
    companion object CREATOR : Parcelable.Creator<AppDescriptor> {
        override fun createFromParcel(parcel: Parcel) = AppDescriptor(parcel)
        override fun newArray(size: Int): Array<AppDescriptor?> = arrayOfNulls(size)
    }
}

class SemanticNode(
    val path: String,
    val text: String,
    val className: String,
    val clickable: Boolean,
    val editable: Boolean,
    val scrollable: Boolean,
) : Parcelable {
    private constructor(parcel: Parcel) : this(
        parcel.readString().orEmpty(), parcel.readString().orEmpty(), parcel.readString().orEmpty(),
        parcel.readInt() != 0, parcel.readInt() != 0, parcel.readInt() != 0,
    )
    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(path); parcel.writeString(text); parcel.writeString(className)
        parcel.writeInt(if (clickable) 1 else 0); parcel.writeInt(if (editable) 1 else 0)
        parcel.writeInt(if (scrollable) 1 else 0)
    }
    override fun describeContents() = 0
    companion object CREATOR : Parcelable.Creator<SemanticNode> {
        override fun createFromParcel(parcel: Parcel) = SemanticNode(parcel)
        override fun newArray(size: Int): Array<SemanticNode?> = arrayOfNulls(size)
    }
}

class SemanticSnapshot(
    val packageName: String,
    val title: String,
    val nodes: List<SemanticNode>,
    val message: String,
) : Parcelable {
    private constructor(parcel: Parcel) : this(
        parcel.readString().orEmpty(), parcel.readString().orEmpty(),
        parcel.createTypedArrayList(SemanticNode.CREATOR).orEmpty(), parcel.readString().orEmpty(),
    )
    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(packageName); parcel.writeString(title)
        parcel.writeTypedList(nodes); parcel.writeString(message)
    }
    override fun describeContents() = 0
    companion object CREATOR : Parcelable.Creator<SemanticSnapshot> {
        override fun createFromParcel(parcel: Parcel) = SemanticSnapshot(parcel)
        override fun newArray(size: Int): Array<SemanticSnapshot?> = arrayOfNulls(size)
    }
}

class AppBridgeReply(
    val status: Int,
    val message: String,
    val token: String,
) : Parcelable {
    private constructor(parcel: Parcel) : this(parcel.readInt(), parcel.readString().orEmpty(), parcel.readString().orEmpty())
    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(status); parcel.writeString(message); parcel.writeString(token)
    }
    override fun describeContents() = 0
    companion object CREATOR : Parcelable.Creator<AppBridgeReply> {
        override fun createFromParcel(parcel: Parcel) = AppBridgeReply(parcel)
        override fun newArray(size: Int): Array<AppBridgeReply?> = arrayOfNulls(size)
    }
}
