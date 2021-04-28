package io.friday.p2p.event;

import io.friday.p2p.entity.FileShareInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.List;

@Data
@AllArgsConstructor
@EqualsAndHashCode
public class FileShareParam implements Serializable {
    List<FileShareInfo> fileShareInfoList;
}
