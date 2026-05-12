package com.fancy.taxiagent.controller;

import com.fancy.taxiagent.annotation.RequirePermission;
import com.fancy.taxiagent.domain.dto.UserPoiDTO;
import com.fancy.taxiagent.domain.entity.UserPOI;
import com.fancy.taxiagent.domain.response.Result;
import com.fancy.taxiagent.domain.vo.POIInfoVO;
import com.fancy.taxiagent.domain.vo.PoiOrderVO;
import com.fancy.taxiagent.service.UserPOIService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/user/poi")
@Slf4j
@RequiredArgsConstructor
public class UserPOIController {

    private final UserPOIService userPOIService;

    /**
     * 获取当前用户所有兴趣点
     */
    @RequirePermission({"USER"})
    @GetMapping
    public Result list() {
        List<UserPOI> poiList = userPOIService.listCurrentUserPOIs();
        return Result.ok(poiList);
    }

    /**
     * 根据ID获取兴趣点
     */
    @RequirePermission({"USER"})
    @GetMapping("/{id}")
    public Result get(@PathVariable Long id) {
        UserPOI poi = userPOIService.findById(id);
        if (poi == null) {
            return Result.fail(404, "兴趣点不存在");
        }
        return Result.ok(poi);
    }

    /**
     * 创建兴趣点
     */
    @RequirePermission({"USER"})
    @PostMapping
    public Result create(@RequestBody POIInfoVO poiInfo) {
        userPOIService.createForCurrentUser(new UserPOI(poiInfo));
        return Result.ok();
    }

    /**
     * 更新兴趣点
     */
    @RequirePermission({"USER"})
    @PutMapping
    public Result update(@RequestBody UserPoiDTO userPOI) {
        userPOIService.update(new UserPOI(userPOI));
        return Result.ok();
    }

    /**
     * 删除兴趣点
     */
    @RequirePermission({"USER"})
    @DeleteMapping("/{id}")
    public Result delete(@PathVariable Long id) {
        userPOIService.deleteById(id);
        return Result.ok();
    }

    /**
     * 获取订单相关POI（家、公司、其他）
     */
    @RequirePermission({"USER"})
    @GetMapping("/order")
    public Result getOrderPoi() {
        PoiOrderVO poiOrderVO = userPOIService.getCurrentUserPoiForOrder();
        return Result.ok(poiOrderVO);
    }
}
