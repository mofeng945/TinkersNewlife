package com.mofengbaizhi.tinkersnewlife.content.energy;

import com.mofengbaizhi.tinkersnewlife.config.ModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import slimeknights.tconstruct.library.recipe.fuel.MeltingFuel;
import slimeknights.tconstruct.library.recipe.fuel.MeltingFuelLookup;

/**
 * <b>来源 ③「匠魂燃料」：烧燃料换 EE</b>（用户口径 §545 ✓）。
 *
 * <h2>规则</h2>
 * <ul>
 *   <li><b>范围</b>：球半径 5，逐个查里面的<b>方块实体</b>（不是方块类型 ✗）✓；</li>
 *   <li>认谁做燃料：<b>匠魂自己的燃料注册表</b> —— {@link MeltingFuelLookup#findFuel(Fluid)} ✓
 *       <b>绝不写死 id</b> ✗（数据包/附属模组加的燃料会自动被认 ✓）；</li>
 *   <li><b>每秒对每个容器"模拟一次烧炼"</b>：按匠魂的燃料消耗规则算出这一秒能烧掉多少燃料 ⇒
 *       折成"能烧几个物品" ⇒ <b>每物品 0.5 EE</b> ✓；</li>
 *   <li>燃料不够一秒的量 ⇒ 这一秒<b>不烧、不给 EE</b> ✓（不做小数赊账 ✗ 免得留下"半份燃料"状态 ✗）。</li>
 * </ul>
 *
 * <h2>⚠ 燃料消耗规则是怎么从 TCon 源码里"查出来"的（源码出处 ✓）</h2>
 * <pre>
 *   TCon 3.11.2.166，文件 slimeknights/tconstruct/smeltery/block/entity/module/FuelModule.java
 *     · int tryLiquidFuel(IFluidHandler handler, boolean consume)      // 行 126~149
 *         FluidStack fluid = handler.getFluidInTank(0);
 *         MeltingFuel recipe = findRecipe(fluid.getFluid());           // → MeltingFuelLookup.findFuel ✓
 *         int amount = recipe.getAmount(fluid.getFluid());             // = 配方 fluid.amount（一批燃料）
 *         if (fluid.getAmount() >= amount) handler.drain(...);
 *         fuel += recipe.getDuration();  temperature = recipe.getTemperature();  rate = recipe.getRate();
 *     · 行 105~107  hasFuel() = fuel > 0
 *   文件 slimeknights/tconstruct/smeltery/block/entity/controller/HeatingStructureBlockEntity.java
 *     · 行 111  protected int fuelRate = 1;
 *     · 行 172~175  case 3 -> if (fuelModule.hasFuel() && fuelRate > 0) fuelModule.decreaseFuel(fuelRate);
 *         ⇒ **每 4 tick 扣一次，每次扣 `rate` 点** ⇒ 折算 = 每 tick 烧 **rate × 0.25** 点燃料 ✓
 *   文件（配方数据）data/tconstruct/recipes/smeltery/melting/fuel/lava.json
 *     { "duration": 100, "fluid": {"amount": 50, "fluid": "minecraft:lava"}, "rate": 10, "temperature": 1000 }
 *   文件（配方数据）data/tconstruct/recipes/smeltery/melting/fuel/blaze.json
 *     { "duration": 150, "fluid": {"amount": 50, "fluid": "tconstruct:blazing_blood"}, "rate": 15, "temperature": 1500 }
 *   文件（配方数据）data/tconstruct/recipes/smeltery/melting/fuel/solid.json
 *     { "rate": 8, "temperature": 800 }        // 固体燃料（加热器里的煤等）：**不是流体** ⇒ 本条不处理 ✗
 * </pre>
 * ⇒ <b>把"每 4 tick 扣 rate 点"的规则积分到 1 秒（20 tick）就是每 tick 扣 rate/4 点</b> ✓
 * （等价于 TCon 自己的"温度 = 燃料消耗速度"口径：岩浆 1000 ⇒ 每 tick 25 mB ⇒ 一桶 1000 mB 刚好烧 40 秒 ✓）。
 * <p>⚠ 注意：这两条 TCon 代码路径（{@code MultitankFuelModule} 与 {@code SolidFuelModule}）
 * 都<b>只认 0 号罐</b>（{@code getFluidInTank(0)}）✓ 本实现照抄这个口径 ✓
 * （冶炼炉的多罐在 TCon 眼里也是"挑一个罐当燃料罐"，不是每罐一起烧 ✓）。
 *
 * <h2>"能烧几个物品"怎么折（诚实项：这是一个口径选择 ✓）</h2>
 * 匠魂的烧炼配方本身有 {@code time}（ticks，见 {@code MeltingRecipe} 的 {@code "time"} 字段 ✓ 全数据包必填 ✓），
 * 但"这一秒烧掉的燃料能烧几个物品"取决于<b>当时在烧的是哪个配方</b> ⇒ 站在台座的角度是拿不到的 ✗。
 * 所以这里取一个<b>固定基准</b> {@code fuel_ticks_per_item}（默认 <b>10</b> tick/物品 ——
 * TCon 3.11.2.166 的 806 个熔炼配方里 {@code "time"} <b>最小就是 9~10 tick</b>，10 是常见档位 ✓）：
 * <pre>
 *     这一秒烧掉的燃料(mB) = temperature × 20 / 4 = temperature × 5
 *     能烧物品数            = (这一秒烧掉的燃料 / 一批燃料量) × (duration / ticks_per_item)
 *     产出 EE               = 能烧物品数 × fuel_ee_per_item（默认 0.5）
 * </pre>
 * 代入默认值：<b>一桶岩浆</b>（50 mB 一批、duration 100、temperature 1000）
 * ⇒ 每秒烧 250 mB = 5 批 ⇒ 5 × (100/10) = 50 物品份 ⇒ <b>25 EE/秒</b> ✓；
 * <b>一桶炽血</b>（50 mB、duration 150、temperature 1500）⇒ 每秒 375 mB = 7.5 批 ⇒
 * 7.5 × 15 = 112.5 物品份 ⇒ <b>56.25 EE/秒</b> ✓。⚠ 这数字很大是<b>规则推出来的</b>（用户要求"不设总上限"✓），
 * 觉得太快就调小 {@code fuel_ee_per_item} 或调大 {@code fuel_ticks_per_item} ✓（都是配置键 ✓）。
 *
 * <h2>容器的认定：Forge 能力，而不是匠魂的类名</h2>
 * 逐个方块实体查 {@code ForgeCapabilities.FLUID_HANDLER} ✓ 这也是<b>匠魂自己</b>找燃料罐的方式
 * （{@code SolidFuelModule#fetchHandlers} 行 118：{@code te.getCapability(ForgeCapabilities.FLUID_HANDLER)} ✓
 * ／{@code MultitankFuelModule#getTankHandlers} 行 96 同 ✓）。
 * <p>⚠ 因此实际能收到的是"<b>把流体能力暴露出来的</b>匠魂容器"：冶炼炉/合金炉/熔化炉控制器、
 * 焦黑与焦褐储罐（{@code TankBlockEntity}）、铸造盆/铸造台（{@code CastingTankBlockEntity}）、
 * 燃料量规（{@code GaugeBlockEntity}）等 ✓ —— <b>这正好就是匠魂会在同一位置当"燃料罐"用的那批方块</b> ✓。
 * <b>不</b>支持的：熔化炉加热器里的<b>固体</b>燃料（煤/木炭…那条走 {@code SolidFuelModule#trySolidFuel} ✓
 * 不是流体 ✗ 本条不做 ✓ 诚实项 ✓）；以及任何<b>不</b>暴露流体能力的容器 ✗。
 *
 * <h2>性能</h2>
 * 只在"台座还能装得下 EE"时才会被调到（见台座 {@code settle()}）✓；
 * 每次扫描复用<b>同一个</b> {@link FluidProbe} 实例（{@link ThreadLocal} ✓）⇒ 稳态零分配 ✓。 * <p>⚠ {@code simulate == true}（tooltip/调试）只读<b>不扣</b>燃料 ✓ —— 这也是它被做成
 * "先 {@code drain(SIMULATE)} 再 {@code drain(EXECUTE)}"的原因：<b>能力给出的可抽量才是唯一的真话</b> ✓。
 */
public final class TConFuelEnergySource implements AmbientEnergySource {

    /** 配置允许清单里写的 id */
    public static final String ID = "tcon_fuel";

    /** ⚠ 契约：{@link IFluidHandler#drain} 的"最多看一眼"口径 —— 用 {@code Integer.MAX_VALUE} 问"到底能抽多少" ✓ */
    private static final int PROBE = Integer.MAX_VALUE;

    /** 复用的探测对象（省掉每秒的 {@code new} ✓；能力本身<b>不</b>会被存起来 ⇒ 不会持有过期的 LazyOptional ✗） */
    private static final ThreadLocal<FluidProbe> PROBE_BOX = ThreadLocal.withInitial(FluidProbe::new);

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String shortName() {
        return "匠魂燃料";
    }

    @Override
    public String summary() {
        return "sphere r=5; each Tinker fluid container is simulated once per second:"
                + " burn = temperature/4 mB per tick, items = (burned/amount)*(duration/ticks_per_item),"
                + " EE = items * fuel_ee_per_item";
    }

    @Override
    public double eePerSecond(Level level, BlockPos pos, boolean simulate) {
        if (!(level instanceof ServerLevel server) || pos == null) return 0.0D;

        int radius = ModConfig.fuelRadius();
        if (radius <= 0) return 0.0D;

        int ticksPerItem = Math.max(1, ModConfig.fuelTicksPerItem());
        double eePerItem = ModConfig.fuelEePerItem();
        if (eePerItem <= 0.0D) return 0.0D;

        FluidProbe probe = PROBE_BOX.get();
        double total = 0.0D;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dy * dy + dz * dz > radius * radius) continue;   // 球不是立方体 ✓
                    BlockPos at = pos.offset(dx, dy, dz);
                    BlockEntity be = server.getBlockEntity(at);
                    if (be == null) continue;
                    total += containerEePerSecond(be, ticksPerItem, eePerItem, simulate, probe);
                }
            }
        }
        return total;
    }

    /**
     * 对<b>一个</b>容器算这一秒能烧出多少 EE（{@code simulate == false} 时真的扣燃料 ✓）。
     *
     * @return 这个容器这一秒贡献的 EE（{@code 0} = 不是容器 / 不是匠魂认的燃料 / 燃料不够烧一秒 ✓）
     */
    private static double containerEePerSecond(BlockEntity be, int ticksPerItem, double eePerItem,
                                               boolean simulate, FluidProbe probe) {
        // 每个方向都试（与匠魂"燃料罐就贴在结构上"的用法一致 ✓）；
        // ⚠ 一块方块实体同时暴露多个方向的能力时只认**第一个有燃料的** ⇒ 不会重复扣同一罐 ✗
        for (Direction side : Direction.values()) {
            LazyOptional<IFluidHandler> cap = be.getCapability(ForgeCapabilities.FLUID_HANDLER, side);
            if (!cap.isPresent()) continue;
            IFluidHandler handler = cap.orElse(null);
            if (handler == null) continue;

            double ee = probe.evaluate(handler, ticksPerItem, eePerItem, simulate);
            // 0 也可能只是"这一面没燃料"（比如只有 DOWN 那面接的是燃料罐 ✓）⇒ 继续试下一面 ✓；
            // 但只要**真的抽到了/能抽到**燃料，就按这个面为准（`handled` 标记 ✓）。
            if (probe.handled) return ee;
        }
        return 0.0D;
    }

    /**
     * 一次"烧一秒"的计算盒（复用 ⇒ 稳态零分配 ✓）。
     * <p>⚠ 它<b>不</b>缓存 {@code IFluidHandler} / {@code LazyOptional} ✓ —— 那些随时会失效 ✓
     * 每次都由调用方现取现用 ✓。
     */
    private static final class FluidProbe {

        /** 这一面"到底是不是燃料罐"（用于跨方向短路：燃料罐只可能贴在某一个面上 ✓） */
        boolean handled;

        double evaluate(IFluidHandler handler, int ticksPerItem, double eePerItem, boolean simulate) {
            handled = false;

            FluidStack stack = handler.getFluidInTank(0);          // 与匠魂同口径：只认 0 号罐 ✓
            if (stack.isEmpty()) return 0.0D;

            Fluid fluid = stack.getFluid();
            MeltingFuel fuel = MeltingFuelLookup.findFuel(fluid);   // ← 匠魂自己的燃料注册表 ✓ 不写死 id ✓
            if (fuel == null) return 0.0D;

            int perBatch = fuel.getAmount(fluid);                  // 一批燃料的量（岩浆 50 mB ✓）
            int temperature = fuel.getTemperature();               // 温度 = 燃料消耗速度（岩浆 1000 ✓）
            int duration = fuel.getDuration();                     // 一批能烧多少 tick（岩浆 100 ✓）
            if (perBatch <= 0 || temperature <= 0 || duration <= 0) return 0.0D;

            // 每 4 tick 扣一次 temperature ⇒ 每秒（20 tick）扣 temperature × 5 ✓（见类注释的源码出处 ✓）
            int want = temperature * 5;
            if (want <= 0) return 0.0D;

            // 先 SIMULATE 问"到底能抽多少"（能力实现说了算 ✓ 也会把"只读的显示代理"挡掉 ✓）
            FluidStack peek = handler.drain(new FluidStack(fluid, want), FluidAction.SIMULATE);
            int available = (peek.isEmpty() || !peek.getFluid().isSame(fluid)) ? 0 : peek.getAmount();
            if (available <= 0) return 0.0D;
            handled = true;                                        // 是燃料罐（哪怕这一秒不够烧 ✓）

            int used = Math.min(want, available);
            if (used < want) return 0.0D;                          // 不够烧满一秒 ⇒ 不烧、不给 EE ✓（不做小数赊账 ✗）

            if (!simulate) {
                FluidStack drained = handler.drain(new FluidStack(fluid, used), FluidAction.EXECUTE);
                if (drained.isEmpty() || !drained.getFluid().isSame(fluid)) return 0.0D;
                used = drained.getAmount();
                if (used <= 0) return 0.0D;
            }

            // 物品份 = (烧掉的量 / 一批的量) × (一批能烧的 tick / 每个物品要的 tick) ✓
            double items = ((double) used / (double) perBatch) * ((double) duration / (double) ticksPerItem);
            return items * eePerItem;
        }
    }
}
