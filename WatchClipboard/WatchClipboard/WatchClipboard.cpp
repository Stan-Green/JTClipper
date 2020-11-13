#include "windows.h"
#include <jni.h>
#include "JTClipper.h"

HINSTANCE  hDLLModule;          //Modules Handle
const char g_szClassName[] = "WatchClipboard";

static JavaVM *jVM = NULL;
//static JNIEnv *javaEnv = NULL;
static jmethodID javaMethodID = NULL;
static jobject javaObject = NULL;

//DLLWindowProc for the new window
LRESULT CALLBACK DLLWindowProc (HWND, UINT, WPARAM, LPARAM);

//Register our windows Class
BOOL RegisterDLLWindowClass(wchar_t szClassName[])
{
    WNDCLASSEX wc;
    wc.hInstance =  hDLLModule;
	wc.lpszClassName = g_szClassName;
    wc.lpfnWndProc = DLLWindowProc;
    wc.style = CS_DBLCLKS;
    wc.cbSize = sizeof (WNDCLASSEX);
    wc.hIcon = LoadIcon (NULL, IDI_APPLICATION);
    wc.hIconSm = LoadIcon (NULL, IDI_APPLICATION);
    wc.hCursor = LoadCursor (NULL, IDC_ARROW);
    wc.lpszMenuName = NULL;
    wc.cbClsExtra = 0;
    wc.cbWndExtra = 0;
    wc.hbrBackground = (HBRUSH) COLOR_BACKGROUND;
    if (!RegisterClassEx (&wc))
	{
		return false;
	}
	else
	{
		return true;
	}
}

//The new thread
DWORD WINAPI ThreadProc( LPVOID lpParam )
{
	MSG messages;
	RegisterDLLWindowClass(L"WatchClipboard");
	HWND hwnd = CreateWindowEx
		(
		  WS_EX_CLIENTEDGE
		, g_szClassName
		, "WatchClipboard"
		, WS_OVERLAPPEDWINDOW
		, CW_USEDEFAULT
		, CW_USEDEFAULT
		, 240
		, 120
		, NULL
		, NULL
		, hDLLModule
		, NULL 
		);
	//Watch the clipboard
	AddClipboardFormatListener(hwnd);
//	ShowWindow (hwnd, SW_SHOWNORMAL);
    while (GetMessage (&messages, NULL, 0, 0))
    {
		TranslateMessage(&messages);
        DispatchMessage(&messages);
    }
    return 1;
}

DWORD WINAPI ClipUpdate( LPVOID lpParam )
{
	JNIEnv *javaEnv = NULL;
	int status;

	status = (jVM)->GetEnv((void**)&javaEnv, JNI_VERSION_1_6);
	jint res = jVM->AttachCurrentThread((void**)&javaEnv,NULL);
	jclass javaClass = javaEnv->FindClass("JTClipper");
	javaMethodID = javaEnv->GetMethodID(javaClass, "clipboardChange", "()V");
	javaEnv->CallVoidMethod(javaObject, javaMethodID);
	res = jVM->DetachCurrentThread();
	return 0;
}

//Our new windows proc
LRESULT CALLBACK DLLWindowProc (HWND hwnd, UINT message, WPARAM wParam, LPARAM lParam)
{

    switch (message)
    {
		case WM_CLIPBOARDUPDATE:
		{
			CreateThread(0, NULL, ClipUpdate, NULL, NULL, NULL);
			break;
		}

		case WM_CLOSE:
            DestroyWindow(hwnd);
			break;

		case WM_DESTROY:
			PostQuitMessage (0);
			break;

		default:
			return DefWindowProc (hwnd, message, wParam, lParam);
			break;
    }
    return 0;
}

BOOL APIENTRY DllMain( HMODULE hModule, DWORD  ul_reason_for_call,LPVOID lpReserved)
{
	if(ul_reason_for_call==DLL_PROCESS_ATTACH) {
		hDLLModule = hModule;
	}
	return TRUE;
}

JNIEXPORT void JNICALL Java_JTClipper_DLLInit(JNIEnv *env, jobject obj)
{
	javaObject =  env->NewGlobalRef(obj);
	env->GetJavaVM(&jVM);
	jclass javaClass = env->FindClass("JTClipper");
	javaMethodID = env->GetMethodID(javaClass, "clipboardChange", "()V");
	if(!javaMethodID)
	{
		MessageBox(NULL,"Bummer - Did not get a method ID", "Error", MB_OK);
	}
	else
	{
		CreateThread(0, NULL, ThreadProc, NULL, NULL, NULL);
	}
}
