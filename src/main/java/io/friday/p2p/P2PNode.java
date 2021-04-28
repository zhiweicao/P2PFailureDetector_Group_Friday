package io.friday.p2p;

import io.friday.p2p.entity.FileInfo;
import io.friday.p2p.entity.FileShareInfo;

import java.util.List;

public interface P2PNode {
    List<FileShareInfo> get(FileInfo fileInfo);
    void share(List<FileInfo> fileInfoList);
    List<FileShareInfo> lookUp();
}
