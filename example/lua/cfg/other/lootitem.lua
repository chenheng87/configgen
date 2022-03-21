local cfg = require "cfg._cfgs"

---@class cfg.other.lootitem
---@field lootid number , 掉落id
---@field itemid number , 掉落物品
---@field chance number , 掉落概率
---@field countmin number , 数量下限
---@field countmax number , 数量上限
---@field get fun(lootid:number,itemid:number):cfg.other.lootitem
---@field all table<any,cfg.other.lootitem>

local this = cfg.other.lootitem

local mk = cfg._mk.table(this, { { 'all', 'get', 1, 2 }, }, nil, nil, 
    'lootid', -- int, 掉落id
    'itemid', -- int, 掉落物品
    'chance', -- int, 掉落概率
    'countmin', -- int, 数量下限
    'countmax' -- int, 数量上限
    )

mk(1, 1001, 20, 1, 1)
mk(2, 22, 20, 20, 80)
mk(2, 40005, 13, 1, 3)
mk(2, 40006, 13, 1, 3)
mk(2, 40007, 13, 1, 3)
mk(2, 40008, 13, 1, 3)
mk(2, 40009, 14, 1, 3)
mk(2, 40010, 14, 1, 3)
mk(3, 22, 10, 100, 150)
mk(3, 40011, 10, 1, 1)
mk(3, 40004, 10, 1, 3)
mk(3, 40005, 12, 2, 4)
mk(3, 40006, 12, 2, 4)
mk(3, 40007, 12, 2, 4)
mk(3, 40008, 12, 2, 4)
mk(3, 40009, 11, 2, 4)
mk(3, 40010, 11, 2, 4)
mk(4, 22, 10, 200, 400)
mk(4, 40011, 10, 1, 1)
mk(4, 40012, 9, 1, 1)
mk(4, 40004, 10, 2, 4)
mk(4, 40003, 1, 1, 2)
mk(4, 40005, 10, 4, 10)
mk(4, 40006, 10, 4, 10)
mk(4, 40007, 10, 4, 10)
mk(4, 40008, 10, 4, 10)
mk(4, 40009, 10, 4, 10)
mk(4, 40010, 10, 4, 10)
mk(5, 30004, 20, 1, 5)
mk(5, 40004, 20, 1, 5)
mk(5, 60002, 20, 1, 5)
mk(5, 80005, 20, 1, 5)
mk(5, 20228, 20, 1, 1)
mk(6, 20228, 100, 1, 1)
mk(7, 23, 100, 100, 100)
mk(8, 20209, 7, 1, 1)
mk(8, 20210, 7, 1, 1)
mk(8, 20520, 7, 1, 1)
mk(8, 20521, 7, 1, 1)
mk(8, 20522, 7, 1, 1)
mk(8, 20523, 8, 1, 1)
mk(8, 20715, 7, 1, 1)
mk(8, 20716, 7, 1, 1)
mk(8, 20717, 8, 1, 1)
mk(8, 20718, 7, 1, 1)
mk(8, 20719, 7, 1, 1)
mk(8, 20720, 7, 1, 1)
mk(8, 20721, 7, 1, 1)
mk(8, 20722, 7, 1, 1)
mk(9, 20723, 8, 1, 1)
mk(9, 20724, 8, 1, 1)
mk(9, 20842, 8, 1, 1)
mk(9, 20843, 8, 1, 1)
mk(9, 20844, 8, 1, 1)
mk(9, 20845, 8, 1, 1)
mk(9, 20846, 8, 1, 1)
mk(9, 20847, 8, 1, 1)
mk(9, 20848, 8, 1, 1)
mk(9, 20849, 7, 1, 1)
mk(9, 20850, 7, 1, 1)
mk(9, 20851, 7, 1, 1)
mk(9, 20852, 7, 1, 1)
mk(10, 20853, 8, 1, 1)
mk(10, 20854, 8, 1, 1)
mk(10, 20855, 8, 1, 1)
mk(10, 20948, 8, 1, 1)
mk(10, 20949, 8, 1, 1)
mk(10, 20950, 8, 1, 1)
mk(10, 20951, 8, 1, 1)
mk(10, 20952, 8, 1, 1)
mk(10, 20953, 8, 1, 1)
mk(10, 20954, 7, 1, 1)
mk(10, 20955, 7, 1, 1)
mk(10, 20956, 7, 1, 1)
mk(10, 20957, 7, 1, 1)

return this