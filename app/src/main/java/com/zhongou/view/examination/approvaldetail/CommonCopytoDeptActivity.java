package com.zhongou.view.examination.approvaldetail;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.zhongou.R;
import com.zhongou.adapter.ContactsCopyToAdapter;
import com.zhongou.application.MyApplication;
import com.zhongou.base.BaseActivity;
import com.zhongou.common.CharacterParser;
import com.zhongou.common.MyException;
import com.zhongou.common.PinyinComparator;
import com.zhongou.db.sqlcontact.SQLiteCoContactdb;
import com.zhongou.dialog.Loading;
import com.zhongou.helper.UserHelper;
import com.zhongou.inject.ViewInject;
import com.zhongou.model.ApprovalSModel;
import com.zhongou.model.ContactsDeptModel;
import com.zhongou.model.ContactsEmployeeModel;
import com.zhongou.model.ContactsSonCOModel;
import com.zhongou.model.MyApprovalModel;
import com.zhongou.utils.PageUtil;
import com.zhongou.widget.SideBar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 抄送 子公司-部门 通讯录
 * Created by sjy on 2017/1/17.
 */

public class CommonCopytoDeptActivity extends BaseActivity {

    //back
    @ViewInject(id = R.id.layout_back, click = "forBack")
    RelativeLayout layout_back;

    //
    @ViewInject(id = R.id.tv_title)
    TextView tv_title;

    //
    @ViewInject(id = R.id.tv_right, click = "forCopyto")
    TextView tv_right;

    //listView
    @ViewInject(id = R.id.country_lvcountry)
    ListView contactsListView;

    //变量
    private MyApprovalModel myApprovalModel;//跳转对象
    private ApprovalSModel approvalSModel;//传送对象
    private String sApprovalemployeeinfos;//转发人ApprovalEmployeeID

    private SideBar sideBar;
    private CharacterParser characterParser;// 汉字转换成拼音的类
    private PinyinComparator pinyinComparator;// 根据拼音来排列ListView里面的数据类

    private static List<ContactsDeptModel> listDeptData;//部门集合
    private static List<ContactsEmployeeModel> listContactApprover;//审批人通讯录 集合
    public static List<ContactsEmployeeModel> selectlist;//checkBox选中数据集合

    private ContactsCopyToAdapter adapter;//通讯录排序适配
    private SQLiteCoContactdb myDb; //sql数据库雷

    //常量
    public static final int POST_SUCCESEE = 15;
    public static final int POST_FAILED = 16;
    public static final int CHASE_DATA = 17;
    public static final int POSTDATA_SUCCESS = 18;//数据转交

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        MyApplication.getInstance().addACT(this);//多界面管理

        setContentView(R.layout.act_apps_examination_myapproval_common_contacts);
        tv_title.setText(getResources().getString(R.string.examination_copyto));

        //获取调转对象

        Intent intent = getIntent();
        String sStoreID = intent.getStringExtra("sStoreID");
        myApprovalModel = (MyApprovalModel) intent.getSerializableExtra("myApprovalModel");

        Log.d("SJY", "sStoreID=" + sStoreID);

        initViews();
        initListener();

        getData(sStoreID);
    }

    /**
     *
     */
    private void initViews() {
        //实例化汉字转拼音类
        characterParser = CharacterParser.getInstance();
        pinyinComparator = new PinyinComparator();
        sideBar = (SideBar) findViewById(R.id.sidrbar);
        myDb = new SQLiteCoContactdb(this);
    }

    /**
     * 控件监听
     */
    private void initListener() {

        //设置右侧触摸监听
        sideBar.setOnTouchingLetterChangedListener(new SideBar.OnTouchingLetterChangedListener() {

            @Override
            public void onTouchingLetterChanged(String s) {
                //该字母首次出现的位置
                int position = adapter.getPositionForSection(s.charAt(0));
                if (position != -1) {
                    contactsListView.setSelection(position);
                }

            }
        });

        //checkbox绑定列表监听
        contactsListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                //因headerView，需要修改position,易错地方，注意postion修改就可以
                int headerViewsCount = contactsListView.getHeaderViewsCount();
                int newPosition = position - headerViewsCount;

                //页面跳转
                if (newPosition < 0) {
                    //选择部门 进入联系人选择 界面
                    int listDataPostion = newPosition + listDeptData.size();//listDeptData修正后的位置

                    Intent intent = new Intent(CommonCopytoDeptActivity.this, CommonCopytoEplActivity.class);
                    intent.putExtra("sDeptID", listDeptData.get(listDataPostion).getsDeptID());//position
                    intent.putExtra("myApprovalModel", myApprovalModel);

                    startActivity(intent);
                } else {
                    //绑定CheckBox选择该联系人
                }

            }
        });

    }

    public void getData(String StoreID) {
        //先判断sql中是否有值
        List<ContactsEmployeeModel> list = myDb.getEmpContactList(SQLiteCoContactdb.EMPLOYEEFLAG);
        Log.d("SJY", "list=" + list.size());
        if (list.size() > 0 && list != null) {
            Log.d("SJY", "走sp缓存");
            sendMessage(CHASE_DATA, list);

        } else if (list == null || list.size() <= 0) {
            Log.d("SJY", "走服务端数据");
            //获取服务端数据
            getDataFromURL(StoreID);

        }
    }

    public void getDataFromURL(final String sStoreID) {
        //
        Loading.run(this, new Runnable() {
            @Override
            public void run() {
                try {
                    //获取部门集合
                    List<ContactsDeptModel> list = UserHelper.getContractsDeptOfSonCO(CommonCopytoDeptActivity.this, sStoreID);

                    sendMessage(POST_SUCCESEE, list);

                } catch (MyException e) {
                    sendMessage(POST_FAILED, e.getMessage());
                }
            }
        });
    }

    @Override
    protected void handleMessage(Message msg) {
        super.handleMessage(msg);
        switch (msg.what) {
            case POST_SUCCESEE:// 服务端数据处理


                listDeptData = (List<ContactsDeptModel>) msg.obj;

//                savetoCoContactSQL(listDeptData);// 部门集合保存到sql

                List<ContactsEmployeeModel> listEmpl = new ArrayList<ContactsEmployeeModel>();
                for (int i = 0; i < listDeptData.size(); i++) {
                    listEmpl.addAll(listDeptData.get(i).getObj());
                }

//                saveToEmployeeSQL(listEmpl);//联系人保存到sql

                //为数据添加首字母
                listContactApprover = filledData(listEmpl);
                // 根据a-z进行排序源数据
                Collections.sort(listContactApprover, pinyinComparator);
                adapter = new ContactsCopyToAdapter(this, listContactApprover);

                //list添加headView
                addListHeadView(listDeptData);

                //数据展示
                contactsListView.setAdapter(adapter);
                break;

            case CHASE_DATA://缓存数据处理

                //由于有缓存数据，从这里获取子公司集合
//                listDeptData = myDb.getSonCOList(SQLiteCoContactdb.SONCOFLAG);

                List<ContactsEmployeeModel> listData = (List<ContactsEmployeeModel>) msg.obj;
                listContactApprover = filledData(listData);//为数据添加首字母

                // 根据a-z进行排序源数据
                Collections.sort(listContactApprover, pinyinComparator);

                //listView添加headView
                addListHeadView(listDeptData);

                //界面展示
                adapter = new ContactsCopyToAdapter(CommonCopytoDeptActivity.this, listContactApprover);
                contactsListView.setAdapter(adapter);
                break;

            case POSTDATA_SUCCESS://选择联系人后的处理
                PageUtil.DisplayToast((String) msg.obj);
                //需要消除两个界面
                MyApplication.getInstance().closeACT();//多界面管理

            case POST_FAILED://
                PageUtil.DisplayToast((String) msg.obj);
                break;

            default:
                break;
        }
    }

    /**
     * 部门集合保存到sql
     */
    private void savetoCoContactSQL(List<ContactsSonCOModel> list) {
        Log.d("SJY", "savetoCoContactSQL部门 数据存储");
//        myDb.addSonCoList(list, SQLiteCoContactdb.SONCOFLAG);
    }

    /**
     * 联系人集合保存
     */
    private void saveToEmployeeSQL(List<ContactsEmployeeModel> list) {
        Log.d("SJY", "saveToEmployeeSQL联系人 数据存储");
//        myDb.addEmplContactList(list, SQLiteCoContactdb.EMPLOYEEFLAG);
    }

    /**
     * 添加headView
     * @param list
     */
    private void addListHeadView(List<ContactsDeptModel> list) {
        Log.d("SJY", "添加headView");
        //为listView添加动态headerView
        LayoutInflater inflator = (LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        for (int j = 0; j < list.size(); j++) {
            //实例化控件
            LinearLayout hearView = (LinearLayout) inflator.inflate(R.layout.item_contacts, null);
            //消除hederView中 部门
            if (j > 0) {
                TextView tv_letter = (TextView) hearView.findViewById(R.id.tv_letter);
                tv_letter.setVisibility(View.GONE);
            }

            //控件展示
            TextView tv_headViewTitle = (TextView) hearView.findViewById(R.id.tv_letter);
            tv_headViewTitle.setText(getResources().getString(R.string.examination_copyto_dept));

            TextView tv_deptOfSon = (TextView) hearView.findViewById(R.id.tv_name);
            tv_deptOfSon.setText(list.get(j).getsDeptName());//部门名称

            contactsListView.addHeaderView(hearView);
        }
    }

    /**
     * 重新修改model,为ListView填充首字母数据
     *
     * @return
     */
    private List<ContactsEmployeeModel> filledData(List<ContactsEmployeeModel> listdata) {
        List<ContactsEmployeeModel> mSortList = new ArrayList<ContactsEmployeeModel>();

        for (int i = 0; i < listdata.size(); i++) {

            //汉字转换成拼音
            String pinyin = characterParser.getSelling(listdata.get(i).getsEmployeeName());
            String sortString = pinyin.substring(0, 1).toUpperCase();

            // 正则表达式，判断首字母是否是英文字母
            if (sortString.matches("[A-Z]")) {
                listdata.get(i).setFirstLetter(sortString.toUpperCase());
            } else {
                listdata.get(i).setFirstLetter("#");
            }

            mSortList.add(listdata.get(i));
        }
        return mSortList;

    }

    /**
     * 根据 搜索框 中的值来过滤数据并更新ListView
     *
     * @param filterStr
     */
    private void filterData(String filterStr) {
        List<ContactsEmployeeModel> filterDateList = new ArrayList<ContactsEmployeeModel>();

        if (TextUtils.isEmpty(filterStr)) {
            filterDateList = listContactApprover;
        } else {
            filterDateList.clear();
            for (ContactsEmployeeModel sortModel : listContactApprover) {
                String name = sortModel.getsEmployeeName();

                if (name.indexOf(filterStr.toString()) != -1 || characterParser.getSelling(name).startsWith(filterStr.toString())) {
                    filterDateList.add(sortModel);
                }
            }
        }

        // 根据a-z进行排序
        Collections.sort(filterDateList, pinyinComparator);
        adapter.updateListView(filterDateList);
    }

    /**
     * 抄送
     *
     * @param view
     */

    public void forCopyto(View view) {
        selectlist = getSelectList(listContactApprover);//获取转交人
        sApprovalemployeeinfos = getList2String(selectlist);//获取
        Log.d("SJY", "转发-确定sApprovalemployeeinfos=" + sApprovalemployeeinfos);

        if (TextUtils.isEmpty(sApprovalemployeeinfos)) {
            PageUtil.DisplayToast("抄送人不能为空");

        }

        //对象赋值处理
        approvalSModel = new ApprovalSModel();
        approvalSModel.setsApplicationid(myApprovalModel.getApprovalID());
        approvalSModel.setsComment(myApprovalModel.getComment());
        approvalSModel.setsApplicationid(myApprovalModel.getApplicationID());
        approvalSModel.setsApplicationtype(myApprovalModel.getApplicationType());
        approvalSModel.setsEmployeeid(myApprovalModel.getEmployeeID());
        approvalSModel.setsStoreid(myApprovalModel.getStoreID());
        approvalSModel.setsApplicationtitle(myApprovalModel.getApplicationTitle());
        approvalSModel.setsApprovalemployeeinfos(sApprovalemployeeinfos);

        Loading.run(this, new Runnable() {
            @Override
            public void run() {

                try {
                    String message = UserHelper.CopyToMyApproval(CommonCopytoDeptActivity.this, approvalSModel);
                    sendMessage(POSTDATA_SUCCESS, message);
                } catch (MyException e) {
                    sendMessage(POST_FAILED, e.getMessage());
                }
            }
        });
    }

    /**
     * 获取选择的抄送人
     *
     * @param list
     * @return
     */
    private List<ContactsEmployeeModel> getSelectList(List<ContactsEmployeeModel> list) {
        List<ContactsEmployeeModel> checkBoxList = new ArrayList<>();
        //遍历
        for (int i = 0; i < list.size(); i++) {
            if (ContactsCopyToAdapter.getIsSelectedMap().get(i) == true) {
                checkBoxList.add(list.get(i));
                Log.d("SJY", "选中的checkbox位置=" + i + "checkbox选中数据长度=" + checkBoxList.size());
            }
        }
        return checkBoxList;
    }

    /**
     * 对参数处理
     *
     * @param list
     * @return
     */
    private String getList2String(List<ContactsEmployeeModel> list) {
        StringBuilder orgString = new StringBuilder();
        //遍历
        for (int i = 0; i < list.size(); i++) {
            orgString.append(list.get(i).getsEmployeeID() + ",");
        }
        //去除末尾逗号
        return orgString.substring(0, orgString.length() - 1);
    }

    /**
     * back
     *
     * @param view
     */
    public void forBack(View view) {
        this.finish();
    }
}