package com.yz8023.adguardtester.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.yz8023.adguardtester.R;
import com.yz8023.adguardtester.model.ProbeResult;
import com.yz8023.adguardtester.model.ProbeStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** 实时结果适配器：按补漏优先级排序，并支持点击行复制检测链接。 */
public final class ResultAdapter extends RecyclerView.Adapter<ResultAdapter.Holder> {

    public interface LinkCopyListener {
        void onCopyLink(String url);
    }

    private static final Comparator<ProbeResult> RESULT_ORDER = new Comparator<ProbeResult>() {
        @Override
        public int compare(ProbeResult a, ProbeResult b) {
            int c = Integer.compare(priority(a.status), priority(b.status));
            if (c != 0) {
                return c;
            }
            c = a.target.category.compareToIgnoreCase(b.target.category);
            if (c != 0) {
                return c;
            }
            return a.target.host.compareToIgnoreCase(b.target.host);
        }
    };

    private final List<ProbeResult> data = new ArrayList<>();
    private final LinkCopyListener linkCopyListener;

    public ResultAdapter(LinkCopyListener linkCopyListener) {
        this.linkCopyListener = linkCopyListener;
    }

    public void add(ProbeResult result) {
        int index = Collections.binarySearch(data, result, RESULT_ORDER);
        if (index < 0) {
            index = -index - 1;
        }
        data.add(index, result);
        notifyItemInserted(index);
    }

    public void clear() {
        int size = data.size();
        data.clear();
        if (size > 0) {
            notifyItemRangeRemoved(0, size);
        }
    }

    /** 批量恢复（旋转屏重建后），按优先级整体排序后一次性插入。 */
    public void addAll(List<ProbeResult> items) {
        if (items.isEmpty()) {
            return;
        }
        data.addAll(items);
        Collections.sort(data, RESULT_ORDER);
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_result, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        final ProbeResult result = data.get(position);
        holder.tvHost.setText(result.target.host);
        holder.tvCopyLink.setText(R.string.copy_link_short);

        StringBuilder meta = new StringBuilder();
        meta.append(result.target.category);
        if (!result.target.vendor.isEmpty()) {
            meta.append(" · ").append(result.target.vendor);
        }
        meta.append(" · ").append(result.status.label);
        meta.append(" · ").append(result.detail);
        holder.tvMeta.setText(meta.toString());
        holder.tvMeta.setTextColor(statusColor(result.status, holder.itemView));

        String description = holder.itemView.getContext().getString(
                R.string.copy_target_link_description, result.target.url);
        holder.itemView.setContentDescription(description);
        View.OnClickListener copyListener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (linkCopyListener != null) {
                    linkCopyListener.onCopyLink(result.target.url);
                }
            }
        };
        holder.itemView.setOnClickListener(copyListener);
        holder.tvCopyLink.setOnClickListener(copyListener);
    }

    private static int priority(ProbeStatus status) {
        switch (status) {
            case NOT_BLOCKED:
                return 0;
            case BLOCKED_DNS:
            case BLOCKED_NETWORK:
                return 1;
            case UNKNOWN:
                return 2;
            case DEAD:
                return 3;
            case CANCELLED:
            default:
                return 4;
        }
    }

    private static int statusColor(ProbeStatus status, View itemView) {
        int res;
        switch (status) {
            case NOT_BLOCKED:
                res = R.color.status_not_blocked;
                break;
            case BLOCKED_DNS:
            case BLOCKED_NETWORK:
                res = R.color.status_blocked;
                break;
            case DEAD:
                res = R.color.status_dead;
                break;
            case CANCELLED:
                res = R.color.status_cancelled;
                break;
            case UNKNOWN:
            default:
                res = R.color.status_unknown;
                break;
        }
        return itemView.getContext().getColor(res);
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView tvHost;
        final TextView tvMeta;
        final TextView tvCopyLink;

        Holder(View view) {
            super(view);
            tvHost = view.findViewById(R.id.tv_host);
            tvMeta = view.findViewById(R.id.tv_meta);
            tvCopyLink = view.findViewById(R.id.tv_copy_link);
        }
    }
}
