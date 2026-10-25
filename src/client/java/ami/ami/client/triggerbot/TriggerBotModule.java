package ami.ami.client.triggerbot;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.Optional;

/**
 * Логика triggerbot под механику боя 1.9+: бьёт цель под прицелом (типы целей задаются в настройках).
 * Миксины только вызывают tick() и onKnockback().
 *
 * Что важно для крита в 1.9+ (PlayerEntity.attack):
 *  - заряд атаки строго больше 0.9;
 *  - fallDistance > 0 и игрок не на земле;
 *  - игрок НЕ бежит (спринт-удар крит не даёт) - поэтому нужен сброс спринта;
 *  - не в воде, не на лестнице, нет слепоты, не на транспорте.
 */
public final class TriggerBotModule {
    /** Включён ли модуль. */
    public static boolean enabled = false;

    /** Минимальный заряд атаки (0.0 - 1.0) для обычного удара. 0.95+ = полный урон. */
    public static float minCooldown = 0.95f;

    /**
     * true  - бить ТОЛЬКО критами (в падении), на земле не бьёт.
     * false - на земле бьёт обычно (при полном кулдауне), в воздухе ждёт падения для крита.
     */
    public static boolean onlyCrits = false;

    /**
     * Максимальная дистанция атаки: от глаз до ближайшей точки хитбокса (как в ваниле).
     * Ваниль: 3.0. Ванильный сервер всё равно режет удары дальше ~4.0 до хитбокса.
     */
    public static double reach = 3.0;

    /**
     * Сброс спринта: перед критом снимает спринт (сервер иначе не засчитает крит),
     * после удара возвращает его, чтобы следующий удар снова был со спринт-отбросом.
     */
    public static boolean sprintReset = true;

    /**
     * Крит от отдачи: после удара по нам удар удерживается, пока нас подбрасывает,
     * и выпускается в первый тик падения.
     */
    public static boolean recoilCrits = true;

    /** Не бить, пока игрок ест (активный предмет с компонентом еды). */
    public static boolean noHitWhileEating = true;

    /** Не бить, пока игрок закрывается щитом (в том числе в первые тики подъёма щита). */
    public static boolean noHitWhileBlocking = true;

    /**
     * Ломать щит: если цель-игрок закрылась щитом (и щит смотрит на нас), на момент удара берём
     * топор из хотбара, бьём им (топор отключает щит), и сразу возвращаем прежний слот.
     * Если топор уже в руке - просто бьём им. Нет топора в хотбаре - бьём как обычно.
     */
    public static boolean breakShield = true;

    /** Бить игроков, на которых надета хотя бы одна часть брони. */
    public static boolean targetPlayers = true;

    /** Бить игроков без брони (все четыре слота брони пусты). */
    public static boolean targetNakedPlayers = true;

    /** Бить животных (AnimalEntity: коровы, свиньи, овцы и т.д.). */
    public static boolean targetAnimals = false;

    /** Бить враждебных мобов (всё, что реализует Monster). */
    public static boolean targetMonsters = false;

    /** Бить невидимые цели. Если выключено - невидимые существа пропускаются. */
    public static boolean targetInvisible = true;

    /**
     * Порог заряда для крита. Крит в ванили требует заряд > 0.9, так что ждать
     * 95% нет смысла: берём минимальное значение с запасом на рассинхрон клиента и сервера.
     */
    private static final float CRIT_CHARGE = 0.91f;

    /** Сколько тиков максимум держим удар, пока подброс от отдачи не начался/не закончился. */
    private static final int RECOIL_HOLD_TICKS = 12;

    /** Сколько тиков ещё действует окно отдачи. */
    private static int recoilTicks = 0;

    private TriggerBotModule() {
    }

    /** Вызывается из ClientPacketListenerMixin, когда пришёл velocity-пакет для локального игрока. */
    public static void onKnockback(ClientPlayerEntity player) {
        if (!enabled || !recoilCrits) {
            return;
        }
        // Нас подбросило вверх: впереди дуга падения, в ней можно ударить критом.
        if (player.getVelocity().y > 0.0) {
            recoilTicks = RECOIL_HOLD_TICKS;
        }
    }

    /** Вызывается из миксина в конце MinecraftClient.tick(). */
    public static void tick(MinecraftClient mc) {
        if (recoilTicks > 0) {
            recoilTicks--;
        }
        if (!enabled) {
            return;
        }

        ClientPlayerEntity player = mc.player;
        if (player == null || mc.world == null || mc.interactionManager == null) {
            return;
        }
        if (mc.currentScreen != null) {
            return;
        }
        if (!player.isAlive() || player.isSpectator()) {
            return;
        }
        if (noHitWhileEating && isEating(player)) {
            return;
        }
        if (noHitWhileBlocking && isBlocking(player)) {
            return;
        }

        // Свежий рейкаст в этом же тике от текущей позиции глаз до хитбокса.
        // mc.crosshairTarget тут отстаёт на тик и режет дистанцию, поэтому он не используется.
        Entity target = findTarget(mc, player);
        if (target == null) {
            return;
        }

        float charge = player.getAttackCooldownProgress(0.5f);
        boolean critBlocked = critBlocked(player);
        boolean airborne = !player.isOnGround() && !critBlocked;
        boolean critReady = airborne && player.fallDistance > 0.0f;

        // Отдача: нас только что подбросило, но мы ещё на земле/поднимаемся.
        // Обычный удар сейчас сжёг бы заряд, а через пару тиков будет крит - ждём.
        boolean launchPending = recoilCrits && recoilTicks > 0 && !critBlocked
                && player.getVelocity().y > 0.0;

        if (critReady) {
            if (charge < CRIT_CHARGE) {
                return;
            }
            attack(mc, player, target, true);
            return;
        }

        if (onlyCrits) {
            return;
        }
        if (airborne || launchPending) {
            // Ждём падения: в воздухе обычный удар не нужен, крит будет через несколько тиков.
            return;
        }
        if (charge < minCooldown) {
            return;
        }
        attack(mc, player, target, false);
    }

    /** Игрок сейчас ест: держит активный предмет, у которого есть компонент еды. */
    private static boolean isEating(ClientPlayerEntity player) {
        if (!player.isUsingItem()) {
            return false;
        }
        ItemStack active = player.getActiveItem();
        return !active.isEmpty() && active.contains(DataComponentTypes.FOOD);
    }

    /**
     * Игрок закрывается щитом. isBlocking() срабатывает только после небольшой задержки
     * подъёма щита, поэтому дополнительно смотрим на сам активный предмет.
     */
    private static boolean isBlocking(ClientPlayerEntity player) {
        if (player.isBlocking()) {
            return true;
        }
        return player.isUsingItem() && player.getActiveItem().isOf(Items.SHIELD);
    }

    /** Подходит ли существо под выбранные в настройках типы целей. */
    private static boolean isValidTarget(Entity entity) {
        if (!(entity instanceof LivingEntity living) || !living.isAlive() || living.isSpectator()) {
            return false;
        }
        if (!targetInvisible && living.isInvisible()) {
            return false;
        }
        if (living instanceof PlayerEntity) {
            return isNaked(living) ? targetNakedPlayers : targetPlayers;
        }
        if (living instanceof Monster) {
            return targetMonsters;
        }
        if (living instanceof AnimalEntity) {
            return targetAnimals;
        }
        return false;
    }

    /** Голый: во всех четырёх слотах брони пусто. */
    private static boolean isNaked(LivingEntity entity) {
        return entity.getEquippedStack(EquipmentSlot.HEAD).isEmpty()
                && entity.getEquippedStack(EquipmentSlot.CHEST).isEmpty()
                && entity.getEquippedStack(EquipmentSlot.LEGS).isEmpty()
                && entity.getEquippedStack(EquipmentSlot.FEET).isEmpty();
    }

    /** Ближайшая подходящая цель, в которую упирается взгляд в пределах reach. */
    private static Entity findTarget(MinecraftClient mc, ClientPlayerEntity player) {
        Vec3d eye = player.getEyePos();
        Vec3d end = eye.add(player.getRotationVec(1.0f).multiply(reach));

        // Берём только существ вокруг луча, а не весь мир.
        Box searchBox = player.getBoundingBox()
                .stretch(player.getRotationVec(1.0f).multiply(reach))
                .expand(1.0);

        Entity best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (Entity other : mc.world.getOtherEntities(player, searchBox, TriggerBotModule::isValidTarget)) {
            Box box = other.getBoundingBox().expand(other.getTargetingMargin());
            double distSq;
            if (box.contains(eye)) {
                distSq = 0.0;
            } else {
                Optional<Vec3d> hit = box.raycast(eye, end);
                if (hit.isEmpty()) {
                    continue;
                }
                distSq = eye.squaredDistanceTo(hit.get());
            }
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = other;
            }
        }
        if (best == null) {
            return null;
        }

        // Блок между нами и целью перекрывает удар.
        HitResult blockHit = player.raycast(reach, 1.0f, false);
        if (blockHit.getType() == HitResult.Type.BLOCK && eye.squaredDistanceTo(blockHit.getPos()) < bestDistSq) {
            return null;
        }
        return best;
    }

    private static void attack(MinecraftClient mc, ClientPlayerEntity player,
                               Entity target, boolean crit) {
        boolean wasSprinting = player.isSprinting();

        if (sprintReset && crit && wasSprinting) {
            // Сервер считает крит по СВОЕМУ состоянию спринта, поэтому пакет STOP должен уйти
            // раньше пакета атаки, а не на следующем тике.
            player.networkHandler.sendPacket(
                    new ClientCommandC2SPacket(player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
            player.setSprinting(false);
        }

        // Щит цели: временно берём топор. Переключение, удар и возврат слота происходят
        // в одном тике, поэтому сервер ни разу не тикает с топором в руке.
        PlayerInventory inventory = player.getInventory();
        int previousSlot = inventory.getSelectedSlot();
        int axeSlot = findShieldBreakSlot(player, target);
        boolean swapped = axeSlot >= 0 && axeSlot != previousSlot;
        if (swapped) {
            inventory.setSelectedSlot(axeSlot);
        }

        // attackEntity() сам досылает пакет выбора слота (топор) перед пакетом атаки.
        mc.interactionManager.attackEntity(player, target);
        player.swingHand(Hand.MAIN_HAND);

        if (swapped) {
            inventory.setSelectedSlot(previousSlot);
            player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(previousSlot));
        }

        if (sprintReset && wasSprinting) {
            restoreSprint(mc, player);
        }
    }

    /**
     * Слот топора для удара по щиту или -1, если ломать щит не нужно/нечем.
     * Если топор уже в руке, возвращает текущий слот (переключение не потребуется).
     */
    private static int findShieldBreakSlot(ClientPlayerEntity player, Entity target) {
        if (!breakShield || !(target instanceof PlayerEntity victim)) {
            return -1;
        }
        if (!isShielding(victim) || !shieldFacesUs(victim, player)) {
            return -1;
        }

        PlayerInventory inventory = player.getInventory();
        int current = inventory.getSelectedSlot();
        if (inventory.getStack(current).isIn(ItemTags.AXES)) {
            return current;
        }
        // Смена слота оборвёт использование предмета (лук, зелье и т.п.).
        if (player.isUsingItem()) {
            return -1;
        }

        // Любой топор отключает щит одинаково; из нескольких берём самый прочный (старший материал).
        int best = -1;
        int bestDurability = -1;
        for (int slot = 0; slot < PlayerInventory.getHotbarSize(); slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (stack.isEmpty() || !stack.isIn(ItemTags.AXES)) {
                continue;
            }
            int durability = stack.getMaxDamage();
            if (durability > bestDurability) {
                bestDurability = durability;
                best = slot;
            }
        }
        return best;
    }

    /** Цель подняла щит: так же, как isBlocking() у нас - плюс проверка активного предмета. */
    private static boolean isShielding(PlayerEntity other) {
        if (other.isBlocking()) {
            return true;
        }
        return other.isUsingItem() && other.getActiveItem().isOf(Items.SHIELD);
    }

    /**
     * Щит закрывает только переднюю полусферу владельца (ванильная проверка в LivingEntity.damage):
     * если цель смотрит от нас, удар и так пройдёт мимо щита, топор не нужен.
     */
    private static boolean shieldFacesUs(PlayerEntity victim, ClientPlayerEntity player) {
        double dx = player.getX() - victim.getX();
        double dz = player.getZ() - victim.getZ();
        Vec3d look = victim.getRotationVec(1.0f);
        return dx * look.x + dz * look.z > 0.0;
    }

    /** Возвращает спринт после удара, если игрок продолжает бежать вперёд. */
    private static void restoreSprint(MinecraftClient mc, ClientPlayerEntity player) {
        boolean hungerOk = player.getHungerManager().getFoodLevel() > 6 || player.getAbilities().allowFlying;
        boolean canSprint = mc.options.forwardKey.isPressed()
                && hungerOk
                && !player.isSneaking()
                && !player.hasVehicle()
                && !player.hasStatusEffect(StatusEffects.BLINDNESS)
                && !player.isTouchingWater();
        if (!canSprint) {
            return;
        }
        player.networkHandler.sendPacket(
                new ClientCommandC2SPacket(player, ClientCommandC2SPacket.Mode.START_SPRINTING));
        player.setSprinting(true);
    }

    /** Состояния, в которых ванильная логика PlayerEntity.attack() крит не даёт. */
    private static boolean critBlocked(ClientPlayerEntity p) {
        return p.isClimbing()
                || p.isTouchingWater()
                || p.hasStatusEffect(StatusEffects.BLINDNESS)
                || p.hasVehicle()
                || p.getAbilities().flying;
    }
}
