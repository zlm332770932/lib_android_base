package com.lib.android.base;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.navigation.NavDestination;
import androidx.navigation.NavOptions;
import androidx.navigation.Navigator;
import androidx.navigation.fragment.FragmentNavigator;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;

/**
 * Create by limin on 2021/4/27.
 *
 *  使用 FixFragmentNavigator 自定义导航
 *
 *  1.先在布局文件中去掉app:navGraph="@navigation/mobile_navigation"
 *
 *  2.然后来到activity，编写如下代码
 *
 *      void onCreate(Bundle savedInstanceState) {
 *          setContentView(R.layout.activity_navigation);
 *          BottomNavigationView navView = findViewById(R.id.nav_view);
 *
 *           //获取页面容器NavHostFragment
 *           Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
 *           //获取导航控制器
 *           NavController navController = NavHostFragment.findNavController(fragment);
 *           //创建自定义的Fragment导航器
 *           FixFragmentNavigator fragmentNavigator =
 *               new FixFragmentNavigator(this, fragment.getChildFragmentManager(), fragment.getId());
 *           //获取导航器提供者
 *           NavigatorProvider provider = navController.getNavigatorProvider();
 *           //把自定义的Fragment导航器添加进去
 *           provider.addNavigator(fragmentNavigator);
 *           //手动创建导航图
 *           NavGraph navGraph = initNavGraph(provider, fragmentNavigator);
 *           //设置导航图
 *           navController.setGraph(navGraph);
 *           //底部导航设置点击事件
 *           navView.setOnNavigationItemSelectedListener(item -> {
 *               navController.navigate(item.getItemId());
 *               return true;
 *           });
 *       }
 *
 *      //手动创建导航图，把3个目的地添加进来
 *       private NavGraph initNavGraph(NavigatorProvider provider, FixFragmentNavigator fragmentNavigator) {
 *           NavGraph navGraph = new NavGraph(new NavGraphNavigator(provider));
 *
 *           //用自定义的导航器来创建目的地
 *           FragmentNavigator.Destination destination1 = fragmentNavigator.createDestination();
 *           destination1.setId(R.id.navigation_home);
 *           destination1.setClassName(HomeFragment.class.getCanonicalName());
 *           destination1.setLabel(getResources().getString(R.string.title_home));
 *           navGraph.addDestination(destination1);
 *
 *           //省略
 *           navGraph.addDestination(destination2);
 *           //省略
 *           navGraph.addDestination(destination3);
 *
 *           navGraph.setStartDestination(R.id.navigation_home);
 *
 *          return navGraph;
 *       }
 *
 * *************************************************************************************************************
 *      前边提到的自定义导航器需要指定名字@Navigator.Name("fixFragment")，
 *      是因为不同类型的目的地（页面）需要使用不同的导航器，在NavigatorProvider里有个map存储了多个导航器，
 *       //NavigatorProvider.java
 *       private final HashMap<String, Navigator<? extends NavDestination>> mNavigators = new HashMap<>();
 *
 *       // "navigation" NavGraphNavigator
 *       // "activity" ActivityNavigator
 *       // "fragment" FragmentNavigator
 *       // "dialog" DialogFragmentNavigator
 *       // "fixFragment" FixFragmentNavigator 这个就是我们自定义的导航器

 *       然后，使用自定义导航器FixFragmentNavigator来createDestination创建目的地，这样就把导航器和目的地绑定在一起了。
 *       可以看出，Navigation的思想是，把各种类型的页面都抽象成目的地Destination，进行统一跳转，不同的导航器则封装了不同
 *       类型页面跳转的实现，由NavController统一调度，而许许多多的目的地则编织成了一个导航图NavGraph。
 *
 **/

//fix 5: 需要指定1个名字，源码里自带的名字有navigation、activity、fragment、dialog
@Navigator.Name("fixFragment")
public class FixFragmentNavigator extends FragmentNavigator {
    private static final String TAG = "FixFragmentNavigator";

    private Context mContext;
    private FragmentManager mFragmentManager;
    private int mContainerId;


    public FixFragmentNavigator(@NonNull Context context, @NonNull FragmentManager manager, int containerId) {
        super(context, manager, containerId);
        mContext = context;
        mFragmentManager = manager;
        mContainerId = containerId;
    }

    @Override
    public NavDestination navigate(@NonNull Destination destination, @Nullable Bundle args,
                                   @Nullable NavOptions navOptions, @Nullable Navigator.Extras navigatorExtras) {
        if (mFragmentManager.isStateSaved()) {
            Log.i(TAG, "Ignoring navigate() call: FragmentManager has already"
                    + " saved its state");
            return null;
        }
        String className = destination.getClassName();
        if (className.charAt(0) == '.') {
            className = mContext.getPackageName() + className;
        }
        //***************** fix 1 ***********
//        final Fragment frag = instantiateFragment(mContext, mFragmentManager,
//                className, args);

        //fix 1: 把类名作为tag，寻找已存在的Fragment
        //（如果想只针对个别fragment进行保活复用，可以在tag上做些标记比如加个前缀，这里不再展开）
        Fragment frag = mFragmentManager.findFragmentByTag(className);
        if (null == frag) {
            //不存在，则创建
            frag = instantiateFragment(mContext, mFragmentManager, className, args);
        }

        //***************** fix 1 ***********  end

        frag.setArguments(args);
        final FragmentTransaction ft = mFragmentManager.beginTransaction();

        int enterAnim = navOptions != null ? navOptions.getEnterAnim() : -1;
        int exitAnim = navOptions != null ? navOptions.getExitAnim() : -1;
        int popEnterAnim = navOptions != null ? navOptions.getPopEnterAnim() : -1;
        int popExitAnim = navOptions != null ? navOptions.getPopExitAnim() : -1;
        if (enterAnim != -1 || exitAnim != -1 || popEnterAnim != -1 || popExitAnim != -1) {
            enterAnim = enterAnim != -1 ? enterAnim : 0;
            exitAnim = exitAnim != -1 ? exitAnim : 0;
            popEnterAnim = popEnterAnim != -1 ? popEnterAnim : 0;
            popExitAnim = popExitAnim != -1 ? popExitAnim : 0;
            ft.setCustomAnimations(enterAnim, exitAnim, popEnterAnim, popExitAnim);
        }
        //***************** fix 2 ***********
//        ft.replace(mContainerId, frag);
        //fix 2: replace换成show和hide
        List<Fragment> fragments = mFragmentManager.getFragments();
        for (Fragment fragment : fragments) {
            ft.hide(fragment);
        }
        if (!frag.isAdded()) {
            ft.add(mContainerId, frag, className);
        }
        ft.show(frag);
        //***************** fix 2 ***********   end

        ft.setPrimaryNavigationFragment(frag);

        final @IdRes int destId = destination.getId();

        //***************** fix 3 ***********
        //fix 3: mBackStack是私有的，而且没有暴露出来，只能反射获取
        ArrayDeque<Integer> mBackStack;
        try {
            Field field = FragmentNavigator.class.getDeclaredField("mBackStack");
            field.setAccessible(true);
            mBackStack = (ArrayDeque<Integer>) field.get(this);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        //***************** fix 3 ***********   end

        final boolean initialNavigation = mBackStack.isEmpty();
        // TODO Build first class singleTop behavior for fragments
        final boolean isSingleTopReplacement = navOptions != null && !initialNavigation
                && navOptions.shouldLaunchSingleTop()
                && mBackStack.peekLast() == destId;

        boolean isAdded;
        if (initialNavigation) {
            isAdded = true;
        } else if (isSingleTopReplacement) {
            // Single Top means we only want one instance on the back stack
            if (mBackStack.size() > 1) {
                // If the Fragment to be replaced is on the FragmentManager's
                // back stack, a simple replace() isn't enough so we
                // remove it from the back stack and put our replacement
                // on the back stack in its place
                mFragmentManager.popBackStack(
                        generateBackStackName(mBackStack.size(), mBackStack.peekLast()),
                        FragmentManager.POP_BACK_STACK_INCLUSIVE);
                ft.addToBackStack(generateBackStackName(mBackStack.size(), destId));
            }
            isAdded = false;
        } else {
            ft.addToBackStack(generateBackStackName(mBackStack.size() + 1, destId));
            isAdded = true;
        }
        if (navigatorExtras instanceof Extras) {
            Extras extras = (Extras) navigatorExtras;
            for (Map.Entry<View, String> sharedElement : extras.getSharedElements().entrySet()) {
                ft.addSharedElement(sharedElement.getKey(), sharedElement.getValue());
            }
        }
        ft.setReorderingAllowed(true);
        ft.commit();
        // The commit succeeded, update our view of the world
        if (isAdded) {
            mBackStack.add(destId);
            return destination;
        } else {
            return null;
        }
    }

    //fix 4: 从父类那边copy过来即可
    @NonNull
    private String generateBackStackName(int backStackIndex, int destId) {
        return backStackIndex + "-" + destId;
    }
}
