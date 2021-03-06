package net.mossol.bot.service.impl;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import net.mossol.bot.model.LocationInfo;
import net.mossol.bot.service.MenuServiceHandler;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.linecorp.centraldogma.client.CentralDogma;
import com.linecorp.centraldogma.client.Watcher;
import com.linecorp.centraldogma.common.Change;
import com.linecorp.centraldogma.common.PushResult;
import com.linecorp.centraldogma.common.Revision;

/**
 * Created by Amos.Doan.Mac on 2017. 12. 6..
 */
@Service
public class MenuServiceHandlerImpl implements MenuServiceHandler {
    private static final Logger logger = LoggerFactory.getLogger(MenuServiceHandlerImpl.class);
    private static final String menuFormat = "메뉴 리스트는 다음과 같아요 멍\n%s";

    private static final String addFormat = "멍멍 %s 추가합니다";
    private static final String removeFormat = "멍멍 %s 가지마세요! 가면 깨뭅니다";
    private static final String removeFail = "멍멍 그런 메뉴 없어요";
    private static final String alreadyExistMenu = "멍멍 이미 있는 곳이에요";
    private static final String addFail = "왈왈! 추가 실패!!";

    private volatile Map<String, LocationInfo> koreaMenu;
    private volatile Map<String, LocationInfo> japanMenu;
    private volatile Map<String, LocationInfo> drinkMenu;

    @Resource
    private CentralDogma centralDogma;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private Watcher<Map<String, LocationInfo>> japanMenuWatcher;

    @Resource
    private Watcher<Map<String, LocationInfo>> koreaMenuWatcher;

    @Resource
    private Watcher<Map<String, LocationInfo>> drinkMenuWatcher;

    private final Random random = new Random();

    private static final Map<String, LocationInfo> koreaDefaultCandidate =
            new HashSet<>(Arrays.asList("부대찌개", "청담소반", "설렁탕", "카레", "닭갈비", "버거킹", "숯불정식", "돈돈정",
                    "브라운돈까스", "차슈멘연구소", "유타로", "짬뽕", "쉑쉑버거", "하야시라이스", "보쌈", "하치돈부리",
                    "홍대개미", "B사감", "콩나물국밥", "순대국밥", "김치찜", "화수목"))
                    .stream()
                    .collect(Collectors.toMap(e -> e, e -> new LocationInfo(e, -1, -1)));
    private static final Map<String, LocationInfo> japanDefaultCandidate =
            new HashSet<>(Arrays.asList("규카츠", "스시", "라멘", "돈카츠", "꼬치", "덴뿌라", "쉑쉑버거", "카레"))
                    .stream()
                    .collect(Collectors.toMap(e -> e, e -> new LocationInfo(e, -1, -1)));
    private static final Map<String, LocationInfo> drinkDefaultCandidate =
            new HashSet<>(Arrays.asList("하누비노"))
                    .stream()
                    .collect(Collectors.toMap(e -> e, e -> new LocationInfo(e, -1, -1)));

    @PostConstruct
    private void init() throws InterruptedException {
        japanMenu = japanDefaultCandidate;
        koreaMenu = koreaDefaultCandidate;
        drinkMenu = drinkDefaultCandidate;

        japanMenuWatcher.watch((revision, menu) -> {
            if (menu == null)  {
                logger.warn("Japan Menu Watch Failed");
                return;
            }
            logger.info("Japan Menu Updated : " + menu);
            japanMenu = menu;
        });

        try {
            japanMenuWatcher.awaitInitialValue(5, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            logger.error("Failed fetch Japan Menu from Central Dogma; Set the Default Menu");
        }

        koreaMenuWatcher.watch((revision, menu) -> {
            if (menu == null)  {
                logger.warn("Korea Menu Watch Failed");
                return;
            }
            logger.info("Korea Menu Updated : " + menu);
            koreaMenu = menu;
        });

        try {
            koreaMenuWatcher.awaitInitialValue(5, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            logger.error("Failed fetch Korea Menu from Central Dogma; Set the Default Menu", e);
        }

        drinkMenuWatcher.watch((revision, menu) -> {
            if (menu == null)  {
                logger.warn("Drink Menu Watch Failed");
                return;
            }
            logger.info("Drink Menu Updated : " + menu);
            drinkMenu = menu;
        });

        try {
            drinkMenuWatcher.awaitInitialValue(5, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            logger.error("Failed fetch Drink Menu from Central Dogma; Set the Default Menu", e);
        }
    }

    private String convertToJsonNode(Map<String, LocationInfo> map) {
        try {
            List<LocationInfo>
                    locationInfos = map.entrySet().stream().map(Map.Entry::getValue).collect(Collectors.toList());
            return objectMapper.writeValueAsString(locationInfos);
        } catch (IOException e) {
            logger.error("Converting Json to Map Failed");
            return null;
        }
    }

    private void updateMenu(Map<String, LocationInfo> menu, FoodType foodType) {
        String jsonMenu = convertToJsonNode(menu);
        if (jsonMenu == null) {
            return;
        }

        String jsonPath = null;
        switch(foodType) {
            case KOREA_FOOD:
                jsonPath = "/koreaMenu.json";
                break;
            case JAPAN_FOOD:
                jsonPath = "/japanMenu.json";
                break;
            case DRINK_FOOD:
                jsonPath = "/drinkMenu.json";
                break;
        }

        logger.info("Update Menu to {} : MENU{}", jsonPath, jsonMenu);
        CompletableFuture<PushResult> future = null;
        try {
            future =
                    centralDogma.push("mossol_menu", "main", Revision.HEAD,
                                      "Add new Menu",
                                      Change.ofJsonUpsert(jsonPath, jsonMenu));
        } catch (Exception e) {
            logger.debug("Menu Update Failed : {} {} ", jsonPath, e);
        }

        future.whenComplete((result, e) -> {
            if (e != null) {
                logger.debug("Menu Update Failed : {} {} ", e.getCause());
                return;
            }
            logger.info("Pushed a commit {} at {}", result.revision(), result.whenAsText());
        });
    }


    private Map<String, LocationInfo> selectMenuType(FoodType type) {
        switch (type) {
            case JAPAN_FOOD:
                return japanMenu;
            case KOREA_FOOD:
                return koreaMenu;
            case DRINK_FOOD:
                return drinkMenu;
            default:
                return koreaMenu;
        }
    }

    @Override
    public String getMenu(FoodType type) {
        logger.debug("getMenu");
        Set<String> menu = selectMenuType(type).entrySet().stream().map(e -> e.getValue().getTitle())
                                               .collect(Collectors.toSet());
        String msg = String.format(menuFormat, String.join("\n", menu));
        logger.debug("DEBUG : {}", msg);
        return msg;
    }

    @SuppressWarnings("unchecked")
    @Override
    public LocationInfo selectMenu(FoodType type) {
        logger.debug("selectMenu");
        Map<String, LocationInfo> menu = selectMenuType(type);
        int idx = (random.nextInt() & Integer.MAX_VALUE) % menu.size();

        logger.debug("idx: {} Selected Menu : {}", idx, menu.keySet());
        Iterator<String> iterator = menu.keySet().iterator();
        for (int i = 0; i < idx; i++) {
            iterator.next();
        }

        String select = iterator.next();
        logger.debug("Selected Menu : {}", select);
        return menu.get(select);
    }

    @Override
    public String addMenu(List<String> foods, FoodType type) {
        final String food = foods.get(0);

        logger.debug("addMenu : " + food);
        if (food.isEmpty()) {
            return addFail;
        }

        Map<String, LocationInfo> menu = selectMenuType(type);
        if (menu.containsKey(food)) {
            return alreadyExistMenu;
        }

        menu.put(food, new LocationInfo(food, -1, -1));
        String msg = String.format(addFormat, food);
        logger.debug("DEBUG : {}", msg);

        updateMenu(menu, type);

        return msg;
    }

    @Override
    public String removeMenu(List<String> foods, FoodType type) {
        final String food = foods.get(0);

        logger.debug("remove Menu : " + food);
        if (food.isEmpty()) {
            return removeFail;
        }

        Map<String, LocationInfo> menu = selectMenuType(type);
        if (!menu.containsKey(food)) {
            return removeFail;
        }

        menu.remove(food);
        String msg = String.format(removeFormat, food);
        logger.debug("DEBUG : {}", msg);

        updateMenu(menu, type);

        return msg;
    }
}
