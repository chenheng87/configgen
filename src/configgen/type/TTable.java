package configgen.type;

import configgen.Logger;
import configgen.Node;
import configgen.define.Table;
import configgen.define.UniqueKey;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TTable extends Node {
    private final Table tableDefine;
    /**
     * 具体的column委派到TBean中
     * 注意这里用组合，但xml配置时column直接配置的table，是继承。
     * 也许xml也用组合比较好，还能多个table共用相同的完整的bean。
     */
    private final TBean tBean;
    /**
     * 枚举对应列
     */
    private Type enumColumn;

    /**
     * 对应sql的概念，一个表可以有一个主键，多个唯一键
     * 主键
     */
    private final Map<String, Type> primaryKey = new LinkedHashMap<>();
    /**
     * 唯一键
     */
    private final List<Map<String, Type>> uniqueKeys = new ArrayList<>();


    public TTable(AllType parent, Table cfg) {
        super(parent, cfg.bean.name);
        this.tableDefine = cfg;
        tBean = new TBean(this, cfg.bean);
    }

    public Table getTableDefine() {
        return tableDefine;
    }

    public TBean getTBean() {
        return tBean;
    }

    public Type getEnumColumnType() {
        return enumColumn;
    }

    public Map<String, Type> getPrimaryKey() {
        return primaryKey;
    }

    public List<Map<String, Type>> getUniqueKeys() {
        return uniqueKeys;
    }

    public void resolve() {
        tBean.resolve();
        if (tableDefine.enumType != Table.EnumType.None) {
            enumColumn = tBean.getColumnMap().get(tableDefine.enumStr);
            if (enumColumn == null) {
                error("枚举列未找到", tableDefine.enumStr);
            } else {
                require(enumColumn instanceof TString, "枚举列必须是字符串", tableDefine.enumStr, enumColumn);
            }
        }
        resolveKey(tableDefine.primaryKey, primaryKey);

        if (tableDefine.enumType != Table.EnumType.None) {
            require(primaryKey.size() == 1, "有枚举的表主键必须是自己或int");
            Type t = primaryKey.values().iterator().next();
            if (!tableDefine.isEnumAsPrimaryKey()) { // 这个是java需要的，用于支持热更
                require(t instanceof TInt, "有枚举的表主键必须是自己或int");
            }
        }

        for (UniqueKey uk : tableDefine.uniqueKeys.values()) {
            Map<String, Type> res = new LinkedHashMap<>();
            resolveKey(uk.keys, res);
            uniqueKeys.add(res);
        }
    }

    private void resolveKey(String[] keys, Map<String, Type> res) {
        for (String k : keys) {
            Type t = tBean.getColumnMap().get(k);
            if (t == null) {
                error("唯一键或主键列未找到", k);
                return;
            }

            if (t.hasText()) {
                Logger.log(fullName() + "的" + k + "有国际化字符串，langSwitch时是不允许的");
            }

            require(null == res.put(k, t), "唯一键或主键列重复", k);

            if (t instanceof TList || t instanceof TMap) {
                error("唯一键或主键类型不支持List，Map", k);
            } else if (t instanceof TBean) {
                if (keys.length != 1) {
                    error("唯一键或主键类型如果是Bean，则必须只有1个", k);
                }
                TBean tbean = (TBean) t;
                for (Type column : tbean.getColumns()) {
                    if (!(column instanceof TPrimitive)) {
                        error("唯一键或主键类型如果是Bean，则Bean里必须只包含基本类型吧，为简单", k);
                    }
                }
            } else {
                if (keys.length == 1) {
                    t.setPrimitiveAndTableKey();
                }
            }
        }
    }


}
