/*
 * Copyright 2019 Jeremy Jamet / Kunzisoft.
 *
 * This file is part of KeePass DX.
 *
 *  KeePass DX is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  KeePass DX is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with KeePass DX.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package com.kunzisoft.keepass.adapters

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SortedList
import androidx.recyclerview.widget.SortedListAdapterCallback
import com.kunzisoft.keepass.R
import com.kunzisoft.keepass.database.SortNodeEnum
import com.kunzisoft.keepass.database.element.*
import com.kunzisoft.keepass.icons.assignDatabaseIcon
import com.kunzisoft.keepass.settings.PreferencesUtil
import java.util.*

class NodeAdapter
/**
 * Create node list adapter with contextMenu or not
 * @param context Context to use
 */
(private val context: Context)
    : RecyclerView.Adapter<NodeAdapter.NodeViewHolder>() {

    private val nodeSortedList: SortedList<NodeVersioned>
    private val inflater: LayoutInflater = LayoutInflater.from(context)

    private var calculateViewTypeTextSize = Array(2) { true} // number of view type
    private var textSizeUnit: Int = TypedValue.COMPLEX_UNIT_PX
    private var prefTextSize: Float = 0F
    private var subtextSize: Float = 0F
    private var infoTextSize: Float = 0F
    private var numberChildrenTextSize: Float = 0F
    private var iconSize: Float = 0F
    private var listSort: SortNodeEnum = SortNodeEnum.DB
    private var ascendingSort: Boolean = true
    private var groupsBeforeSort: Boolean = true
    private var recycleBinBottomSort: Boolean = true
    private var showUserNames: Boolean = true
    private var showNumberEntries: Boolean = true

    private var actionNodesList = LinkedList<NodeVersioned>()
    private var nodeClickCallback: NodeClickCallback? = null

    private val mDatabase: Database

    private val iconGroupColor: Int
    private val iconEntryColor: Int

    /**
     * Determine if the adapter contains or not any element
     * @return true if the list is empty
     */
    val isEmpty: Boolean
        get() = nodeSortedList.size() <= 0

    init {
        assignPreferences()

        this.nodeSortedList = SortedList(NodeVersioned::class.java, object : SortedListAdapterCallback<NodeVersioned>(this) {
            override fun compare(item1: NodeVersioned, item2: NodeVersioned): Int {
                return listSort.getNodeComparator(ascendingSort, groupsBeforeSort, recycleBinBottomSort).compare(item1, item2)
            }

            override fun areContentsTheSame(oldItem: NodeVersioned, newItem: NodeVersioned): Boolean {
                return oldItem.type == newItem.type
                        && oldItem.title == newItem.title
                        && oldItem.icon == newItem.icon
            }

            override fun areItemsTheSame(item1: NodeVersioned, item2: NodeVersioned): Boolean {
                return item1 == item2
            }
        })

        // Database
        this.mDatabase = Database.getInstance()

        // Retrieve the color to tint the icon
        val taTextColorPrimary = context.theme.obtainStyledAttributes(intArrayOf(android.R.attr.textColorPrimary))
        this.iconGroupColor = taTextColorPrimary.getColor(0, Color.BLACK)
        taTextColorPrimary.recycle()
        // In two times to fix bug compilation
        val taTextColor = context.theme.obtainStyledAttributes(intArrayOf(android.R.attr.textColor))
        this.iconEntryColor = taTextColor.getColor(0, Color.BLACK)
        taTextColor.recycle()
    }

    private fun assignPreferences() {
        this.prefTextSize = PreferencesUtil.getListTextSize(context)
        this.infoTextSize = context.resources.getDimension(R.dimen.list_medium_size_default) * prefTextSize
        this.subtextSize = context.resources.getDimension(R.dimen.list_small_size_default) * prefTextSize
        this.numberChildrenTextSize = context.resources.getDimension(R.dimen.list_tiny_size_default) * prefTextSize
        this.iconSize = context.resources.getDimension(R.dimen.list_icon_size_default) * prefTextSize

        this.listSort = PreferencesUtil.getListSort(context)
        this.ascendingSort = PreferencesUtil.getAscendingSort(context)
        this.groupsBeforeSort = PreferencesUtil.getGroupsBeforeSort(context)
        this.recycleBinBottomSort = PreferencesUtil.getRecycleBinBottomSort(context)
        this.showUserNames = PreferencesUtil.showUsernamesListEntries(context)
        this.showNumberEntries = PreferencesUtil.showNumberEntries(context)

        // Reinit textSize for all view type
        calculateViewTypeTextSize.forEachIndexed { index, _ -> calculateViewTypeTextSize[index] = true }
    }

    /**
     * Rebuild the list by clear and build children from the group
     */
    fun rebuildList(group: GroupVersioned) {
        this.nodeSortedList.clear()
        assignPreferences()
        try {
            this.nodeSortedList.addAll(group.getChildren())
        } catch (e: Exception) {
            Log.e(TAG, "Can't add node elements to the list", e)
            Toast.makeText(context, "Can't add node elements to the list : " + e.message, Toast.LENGTH_LONG).show()
        }
    }

    fun contains(node: NodeVersioned): Boolean {
        return nodeSortedList.indexOf(node) != SortedList.INVALID_POSITION
    }

    /**
     * Add a node to the list
     * @param node Node to add
     */
    fun addNode(node: NodeVersioned) {
        nodeSortedList.add(node)
    }

    /**
     * Remove a node in the list
     * @param node Node to delete
     */
    fun removeNode(node: NodeVersioned) {
        nodeSortedList.remove(node)
    }

    /**
     * Remove a node at [position] in the list
     */
    fun removeNodeAt(position: Int) {
        nodeSortedList.removeItemAt(position)
        // Refresh all the next items
        notifyItemRangeChanged(position, nodeSortedList.size() - position)
    }

    /**
     * Update a node in the list
     * @param oldNode Node before the update
     * @param newNode Node after the update
     */
    fun updateNode(oldNode: NodeVersioned, newNode: NodeVersioned) {
        nodeSortedList.beginBatchedUpdates()
        nodeSortedList.remove(oldNode)
        nodeSortedList.add(newNode)
        nodeSortedList.endBatchedUpdates()
    }

    fun setActionNodes(actionNodes: List<NodeVersioned>) {
        this.actionNodesList.apply {
            clear()
            addAll(actionNodes)
        }
    }

    fun unselectActionNodes() {
        actionNodesList.forEach {
            notifyItemChanged(nodeSortedList.indexOf(it))
        }
        this.actionNodesList.apply {
            clear()
        }
    }

    /**
     * Notify a change sort of the list
     */
    fun notifyChangeSort(sortNodeEnum: SortNodeEnum, ascending: Boolean, groupsBefore: Boolean) {
        this.listSort = sortNodeEnum
        this.ascendingSort = ascending
        this.groupsBeforeSort = groupsBefore
    }

    override fun getItemViewType(position: Int): Int {
        return nodeSortedList.get(position).type.ordinal
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NodeViewHolder {
        val view: View = if (viewType == Type.GROUP.ordinal) {
            inflater.inflate(R.layout.item_list_nodes_group, parent, false)
        } else {
            inflater.inflate(R.layout.item_list_nodes_entry, parent, false)
        }
        return NodeViewHolder(view)
    }

    override fun onBindViewHolder(holder: NodeViewHolder, position: Int) {
        val subNode = nodeSortedList.get(position)
        // Assign image
        val iconColor = when (subNode.type) {
            Type.GROUP -> iconGroupColor
            Type.ENTRY -> iconEntryColor
        }
        holder.icon.apply {
            assignDatabaseIcon(mDatabase.drawFactory, subNode.icon, iconColor)
            // Relative size of the icon
            layoutParams?.apply {
                height = iconSize.toInt()
                width = iconSize.toInt()
            }
        }
        // Assign text
        holder.text.apply {
            text = subNode.title
            setTextSize(textSizeUnit, infoTextSize)
            paintFlags = if (subNode.isCurrentlyExpires)
                paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            else
                paintFlags and Paint.STRIKE_THRU_TEXT_FLAG
        }
        // Assign click
        holder.container.setOnClickListener {
            nodeClickCallback?.onNodeClick(subNode, position)
        }
        holder.container.setOnLongClickListener {
            nodeClickCallback?.onNodeLongClick(subNode, position) ?: false
        }

        holder.container.isSelected = actionNodesList.contains(subNode)

        // Add subText with username
        holder.subText.apply {
            text = ""
            paintFlags = if (subNode.isCurrentlyExpires)
                paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            else
                paintFlags and Paint.STRIKE_THRU_TEXT_FLAG
            visibility = View.GONE
            if (subNode.type == Type.ENTRY) {
                val entry = subNode as EntryVersioned

                mDatabase.startManageEntry(entry)

                holder.text.text = entry.getVisualTitle()

                val username = entry.username
                if (showUserNames && username.isNotEmpty()) {
                    visibility = View.VISIBLE
                    text = username
                    setTextSize(textSizeUnit, subtextSize)
                }

                mDatabase.stopManageEntry(entry)
            }
        }

        // Add number of entries in groups
        if (subNode.type == Type.GROUP) {
            if (showNumberEntries) {
                holder.numberChildren?.apply {
                    text = (subNode as GroupVersioned).getChildEntries(true).size.toString()
                    setTextSize(textSizeUnit, numberChildrenTextSize)
                    visibility = View.VISIBLE
                }
            } else {
                holder.numberChildren?.visibility = View.GONE
            }
        }
    }

    override fun getItemCount(): Int {
        return nodeSortedList.size()
    }

    /**
     * Assign a listener when a node is clicked
     */
    fun setOnNodeClickListener(nodeClickCallback: NodeClickCallback?) {
        this.nodeClickCallback = nodeClickCallback
    }

    /**
     * Callback listener to redefine to do an action when a node is click
     */
    interface NodeClickCallback {
        fun onNodeClick(node: NodeVersioned, position: Int)
        fun onNodeLongClick(node: NodeVersioned, position: Int): Boolean
    }

    class NodeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var container: View = itemView.findViewById(R.id.node_container)
        var icon: ImageView = itemView.findViewById(R.id.node_icon)
        var text: TextView = itemView.findViewById(R.id.node_text)
        var subText: TextView = itemView.findViewById(R.id.node_subtext)
        var numberChildren: TextView? = itemView.findViewById(R.id.node_child_numbers)
    }

    companion object {
        private val TAG = NodeAdapter::class.java.name
    }
}
